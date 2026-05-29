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

    private final JdbcClient jdbcClient;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
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
    }

    @Transactional
    public AuthResponse signUp(SignUpRequest request) {
        if (findUserByEmail(request.email()) != null) {
            throw new AuthException(HttpStatus.CONFLICT, "Email is already registered");
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

        createProfileIfMissing(userId, request.email());
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

    private String findEmailById(String userId) {
        return jdbcClient.sql("SELECT email FROM auth_users WHERE id = :id")
                .param("id", userId)
                .query(String.class)
                .optional()
                .orElse("");
    }

    private void createProfileIfMissing(String userId, String email) {
        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM profiles WHERE id = :id")
                .param("id", userId)
                .query(Integer.class)
                .single();
        if (count != null && count > 0) {
            return;
        }

        String localPart = email.substring(0, email.indexOf("@"));
        String handle = "@" + localPart.replaceAll("[^a-zA-Z0-9._]", "").toLowerCase();
        if (handle.length() < 4) {
            handle = "@user" + userId.substring(0, 6);
        }

        jdbcClient.sql("""
                        INSERT INTO profiles (id, name, handle, email, bio)
                        VALUES (:id, :name, :handle, :email, '')
                        """)
                .param("id", userId)
                .param("name", localPart)
                .param("handle", handle)
                .param("email", email)
                .update();
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
        return Map.of("id", id, "email", email);
    }

    private record AuthUser(String id, String email, String passwordHash) {
    }

    private record SessionRow(String userId, String email) {
    }
}
