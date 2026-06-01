package mystreak.backend.auth;

import jakarta.annotation.PostConstruct;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final long ACCESS_TOKEN_TTL_SECONDS = 60L * 60L * 24L * 7L;
    private static final long REFRESH_TOKEN_TTL_SECONDS = 60L * 60L * 24L * 30L;

    private static final String HANDLE_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

    private final JdbcClient jdbcClient;
    private final KakaoUserClient kakaoUserClient;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(JdbcClient jdbcClient, KakaoUserClient kakaoUserClient) {
        this.jdbcClient = jdbcClient;
        this.kakaoUserClient = kakaoUserClient;
    }

    @PostConstruct
    void ensureAuthTables() {
        jdbcClient.sql("""
                        CREATE TABLE IF NOT EXISTS auth_users (
                            id VARCHAR(64) PRIMARY KEY,
                            email VARCHAR(255) NOT NULL UNIQUE,
                            password_hash VARCHAR(255) NOT NULL,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                        """)
                .update();
        jdbcClient.sql("""
                        CREATE TABLE IF NOT EXISTS auth_sessions (
                            access_token VARCHAR(255) PRIMARY KEY,
                            refresh_token VARCHAR(255) NOT NULL UNIQUE,
                            user_id VARCHAR(64) NOT NULL,
                            expires_at TIMESTAMP NOT NULL,
                            refresh_expires_at TIMESTAMP NOT NULL,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                        """)
                .update();
        migrateAuthUsersForSocialLogin();
    }

    /**
     * 소셜 로그인 지원을 위한 auth_users 스키마 보정. (MySQL 8은 ADD COLUMN IF NOT EXISTS 미지원이라
     * information_schema 로 존재 여부를 확인한 뒤 ALTER 한다.)
     */
    private void migrateAuthUsersForSocialLogin() {
        if (!columnExists("auth_users", "provider")) {
            jdbcClient.sql("ALTER TABLE auth_users ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'local'").update();
        }
        if (!columnExists("auth_users", "provider_id")) {
            jdbcClient.sql("ALTER TABLE auth_users ADD COLUMN provider_id VARCHAR(128)").update();
        }
        // 소셜 사용자는 비밀번호/이메일이 없을 수 있으므로 NULL 허용으로 완화한다.
        jdbcClient.sql("ALTER TABLE auth_users MODIFY COLUMN password_hash VARCHAR(255) NULL").update();
        jdbcClient.sql("ALTER TABLE auth_users MODIFY COLUMN email VARCHAR(255) NULL").update();
        if (!indexExists("auth_users", "uk_auth_users_provider")) {
            jdbcClient.sql("ALTER TABLE auth_users ADD UNIQUE KEY uk_auth_users_provider (provider, provider_id)").update();
        }
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = DATABASE() AND table_name = :table AND column_name = :column
                        """)
                .param("table", table)
                .param("column", column)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    private boolean indexExists(String table, String index) {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*) FROM information_schema.statistics
                        WHERE table_schema = DATABASE() AND table_name = :table AND index_name = :index
                        """)
                .param("table", table)
                .param("index", index)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    @Transactional
    public AuthResponse signUp(SignUpRequest request) {
        if (findUserByEmail(request.email()) != null) {
            throw new AuthException(HttpStatus.CONFLICT, "Email is already registered");
        }

        String handle = normalizeHandle(request.handle());
        if (isHandleTaken(handle)) {
            throw new AuthException(HttpStatus.CONFLICT, "이미 사용 중인 아이디예요");
        }

        String userId = UUID.randomUUID().toString();
        jdbcClient.sql("""
                        INSERT INTO auth_users (id, email, password_hash)
                        VALUES (:id, :email, :passwordHash)
                        """)
                .param("id", userId)
                .param("email", request.email())
                .param("passwordHash", passwordEncoder.encode(request.password()))
                .update();

        createProfile(userId, request.email(), request.name().trim(), handle);
        return createSession(userId, request.email());
    }

    @Transactional
    public AuthResponse signIn(SignInRequest request) {
        AuthUser user = findUserByEmail(request.email());
        if (user == null || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Invalid login credentials");
        }

        return createSession(user.id(), user.email());
    }

    @Transactional
    public AuthResponse kakaoLogin(String code, String redirectUri) {
        String kakaoAccessToken = kakaoUserClient.exchangeCodeForToken(code, redirectUri);
        KakaoUserClient.KakaoUser kakaoUser = kakaoUserClient.fetchUser(kakaoAccessToken);

        String userId = findUserIdByProvider("kakao", kakaoUser.id());
        String email;
        if (userId == null) {
            userId = UUID.randomUUID().toString();
            email = kakaoUser.email();
            String name = (kakaoUser.nickname() != null && !kakaoUser.nickname().isBlank())
                    ? kakaoUser.nickname().trim()
                    : "카카오 사용자";
            String handle = generateUniqueHandle(kakaoUser.nickname());

            jdbcClient.sql("""
                            INSERT INTO auth_users (id, email, password_hash, provider, provider_id)
                            VALUES (:id, :email, NULL, 'kakao', :providerId)
                            """)
                    .param("id", userId)
                    .param("email", email)
                    .param("providerId", kakaoUser.id())
                    .update();

            createProfile(userId, email != null ? email : "", name, handle);
        } else {
            email = findEmailById(userId);
        }

        return createSession(userId, email);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        SessionRow session = jdbcClient.sql("""
                        SELECT s.user_id, u.email
                        FROM auth_sessions s
                        JOIN auth_users u ON u.id = s.user_id
                        WHERE s.refresh_token = :refreshToken
                          AND s.refresh_expires_at > CURRENT_TIMESTAMP
                        """)
                .param("refreshToken", request.refreshToken())
                .query((rs, rowNum) -> new SessionRow(rs.getString("user_id"), rs.getString("email")))
                .optional()
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        jdbcClient.sql("DELETE FROM auth_sessions WHERE refresh_token = :refreshToken")
                .param("refreshToken", request.refreshToken())
                .update();
        return createSession(session.userId(), session.email());
    }

    @Transactional
    public void logout(String bearerToken) {
        String accessToken = requireAccessToken(bearerToken);
        jdbcClient.sql("DELETE FROM auth_sessions WHERE access_token = :accessToken")
                .param("accessToken", accessToken)
                .update();
    }

    public Map<String, Object> me(String bearerToken) {
        String userId = requireUserId(bearerToken);
        return userMap(userId, findEmailById(userId));
    }

    public String requireUserId(String authorization) {
        String accessToken = requireAccessToken(authorization);
        return jdbcClient.sql("""
                        SELECT user_id
                        FROM auth_sessions
                        WHERE access_token = :accessToken
                          AND expires_at > CURRENT_TIMESTAMP
                        """)
                .param("accessToken", accessToken)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "Invalid access token"));
    }

    private AuthResponse createSession(String userId, String email) {
        long now = Instant.now().getEpochSecond();
        long expiresAt = now + ACCESS_TOKEN_TTL_SECONDS;
        String accessToken = randomToken();
        String refreshToken = randomToken();

        jdbcClient.sql("""
                        INSERT INTO auth_sessions (access_token, refresh_token, user_id, expires_at, refresh_expires_at)
                        VALUES (:accessToken, :refreshToken, :userId, :expiresAt, :refreshExpiresAt)
                        """)
                .param("accessToken", accessToken)
                .param("refreshToken", refreshToken)
                .param("userId", userId)
                .param("expiresAt", java.sql.Timestamp.from(Instant.ofEpochSecond(expiresAt)))
                .param("refreshExpiresAt", java.sql.Timestamp.from(Instant.ofEpochSecond(now + REFRESH_TOKEN_TTL_SECONDS)))
                .update();

        return new AuthResponse(accessToken, refreshToken, ACCESS_TOKEN_TTL_SECONDS, expiresAt, "bearer", userMap(userId, email));
    }

    private AuthUser findUserByEmail(String email) {
        return jdbcClient.sql("""
                        SELECT id, email, password_hash
                        FROM auth_users
                        WHERE email = :email
                        """)
                .param("email", email)
                .query((rs, rowNum) -> new AuthUser(
                        rs.getString("id"),
                        rs.getString("email"),
                        rs.getString("password_hash")
                ))
                .optional()
                .orElse(null);
    }

    private String findUserIdByProvider(String provider, String providerId) {
        return jdbcClient.sql("""
                        SELECT id FROM auth_users
                        WHERE provider = :provider AND provider_id = :providerId
                        """)
                .param("provider", provider)
                .param("providerId", providerId)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private String findEmailById(String userId) {
        return jdbcClient.sql("SELECT email FROM auth_users WHERE id = :id")
                .param("id", userId)
                .query(String.class)
                .optional()
                .orElse("");
    }

    private String generateUniqueHandle(String nickname) {
        String base = sanitizeHandleBase(nickname);
        String candidate = "@" + base;
        if (!isHandleTaken(candidate)) {
            return candidate;
        }
        for (int i = 0; i < 20; i++) {
            candidate = "@" + base + randomHandleSuffix(4);
            if (!isHandleTaken(candidate)) {
                return candidate;
            }
        }
        return "@" + base + UUID.randomUUID().toString().substring(0, 8);
    }

    private String sanitizeHandleBase(String nickname) {
        String ascii = nickname == null ? "" : nickname.replaceAll("[^a-zA-Z0-9._]", "");
        if (ascii.length() < 3) {
            ascii = "kakao" + randomHandleSuffix(4);
        }
        if (ascii.length() > 20) {
            ascii = ascii.substring(0, 20);
        }
        return ascii;
    }

    private String randomHandleSuffix(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(HANDLE_CHARS.charAt(secureRandom.nextInt(HANDLE_CHARS.length())));
        }
        return sb.toString();
    }

    private void createProfile(String userId, String email, String name, String handle) {
        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM profiles WHERE id = :id")
                .param("id", userId)
                .query(Integer.class)
                .single();
        if (count != null && count > 0) {
            return;
        }

        jdbcClient.sql("""
                        INSERT INTO profiles (id, name, handle, email, bio)
                        VALUES (:id, :name, :handle, :email, '')
                        """)
                .param("id", userId)
                .param("name", name)
                .param("handle", handle)
                .param("email", email)
                .update();
    }

    private String normalizeHandle(String handle) {
        String trimmed = handle.trim();
        return trimmed.startsWith("@") ? trimmed : "@" + trimmed;
    }

    private boolean isHandleTaken(String handle) {
        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM profiles WHERE LOWER(handle) = LOWER(:handle)")
                .param("handle", handle)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    private String requireAccessToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Bearer token is required");
        }
        return authorization.substring(7);
    }

    private String randomToken() {
        byte[] token = new byte[32];
        secureRandom.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private Map<String, Object> userMap(String id, String email) {
        return Map.of("id", id, "email", email != null ? email : "");
    }

    private record AuthUser(String id, String email, String passwordHash) {
    }

    private record SessionRow(String userId, String email) {
    }
}
