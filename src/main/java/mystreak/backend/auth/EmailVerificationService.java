package mystreak.backend.auth;

import jakarta.annotation.PostConstruct;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {

    private static final long CODE_TTL_SECONDS = 60L * 10L;
    private static final int MAX_ATTEMPTS = 5;

    private final JdbcClient jdbcClient;
    private final GoogleMailService googleMailService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailVerificationService(JdbcClient jdbcClient, GoogleMailService googleMailService) {
        this.jdbcClient = jdbcClient;
        this.googleMailService = googleMailService;
    }

    @PostConstruct
    void ensureEmailVerificationTable() {
        jdbcClient.sql("""
                        CREATE TABLE IF NOT EXISTS email_verifications (
                            id VARCHAR(64) PRIMARY KEY,
                            email VARCHAR(255) NOT NULL,
                            purpose VARCHAR(40) NOT NULL,
                            code_hash VARCHAR(255) NOT NULL,
                            attempts INT NOT NULL DEFAULT 0,
                            expires_at TIMESTAMP NOT NULL,
                            verified_at TIMESTAMP NULL,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                        """)
                .update();
    }

    @Transactional
    public EmailVerificationResponse sendCode(EmailVerificationSendRequest request) {
        String email = normalizeEmail(request.email());
        String purpose = normalizePurpose(request.purpose());
        String code = generateCode();
        long expiresAt = Instant.now().getEpochSecond() + CODE_TTL_SECONDS;

        jdbcClient.sql("""
                        INSERT INTO email_verifications (id, email, purpose, code_hash, expires_at)
                        VALUES (:id, :email, :purpose, :codeHash, :expiresAt)
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("email", email)
                .param("purpose", purpose)
                .param("codeHash", passwordEncoder.encode(code))
                .param("expiresAt", java.sql.Timestamp.from(Instant.ofEpochSecond(expiresAt)))
                .update();

        googleMailService.sendVerificationCode(email, code);
        return new EmailVerificationResponse(false, expiresAt);
    }

    @Transactional
    public EmailVerificationResponse confirmCode(EmailVerificationConfirmRequest request) {
        String email = normalizeEmail(request.email());
        String purpose = normalizePurpose(request.purpose());
        VerificationRow row = latestVerification(email, purpose);

        if (row == null) {
            throw new AuthException(HttpStatus.NOT_FOUND, "인증 요청을 찾을 수 없어요");
        }
        if (row.verified()) {
            return new EmailVerificationResponse(true, row.expiresAt());
        }
        if (row.expiresAt() <= Instant.now().getEpochSecond()) {
            throw new AuthException(HttpStatus.GONE, "인증 코드가 만료됐어요");
        }
        if (row.attempts() >= MAX_ATTEMPTS) {
            throw new AuthException(HttpStatus.TOO_MANY_REQUESTS, "인증 시도 횟수를 초과했어요");
        }

        if (!passwordEncoder.matches(request.code(), row.codeHash())) {
            jdbcClient.sql("""
                            UPDATE email_verifications
                            SET attempts = attempts + 1
                            WHERE id = :id
                            """)
                    .param("id", row.id())
                    .update();
            throw new AuthException(HttpStatus.UNAUTHORIZED, "인증 코드가 올바르지 않아요");
        }

        jdbcClient.sql("""
                        UPDATE email_verifications
                        SET verified_at = CURRENT_TIMESTAMP
                        WHERE id = :id
                        """)
                .param("id", row.id())
                .update();
        return new EmailVerificationResponse(true, row.expiresAt());
    }

    public boolean isVerified(String email, String purpose) {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM email_verifications
                        WHERE email = :email
                          AND purpose = :purpose
                          AND verified_at IS NOT NULL
                          AND expires_at > CURRENT_TIMESTAMP
                        """)
                .param("email", normalizeEmail(email))
                .param("purpose", normalizePurpose(purpose))
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    private VerificationRow latestVerification(String email, String purpose) {
        return jdbcClient.sql("""
                        SELECT id, code_hash, attempts, UNIX_TIMESTAMP(expires_at) AS expires_at, verified_at IS NOT NULL AS verified
                        FROM email_verifications
                        WHERE email = :email AND purpose = :purpose
                        ORDER BY created_at DESC
                        LIMIT 1
                        """)
                .param("email", email)
                .param("purpose", purpose)
                .query((rs, rowNum) -> new VerificationRow(
                        rs.getString("id"),
                        rs.getString("code_hash"),
                        rs.getInt("attempts"),
                        rs.getLong("expires_at"),
                        rs.getBoolean("verified")
                ))
                .optional()
                .orElse(null);
    }

    private String generateCode() {
        return "%06d".formatted(secureRandom.nextInt(1_000_000));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String normalizePurpose(String purpose) {
        return purpose.trim().toLowerCase();
    }

    private record VerificationRow(String id, String codeHash, int attempts, long expiresAt, boolean verified) {
    }
}
