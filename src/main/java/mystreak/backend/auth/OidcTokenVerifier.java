package mystreak.backend.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.URI;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class OidcTokenVerifier {

    private final List<String> googleAudiences;
    private final List<String> appleAudiences;
    private final ConfigurableJWTProcessor<SecurityContext> googleProcessor;
    private final ConfigurableJWTProcessor<SecurityContext> appleProcessor;

    public OidcTokenVerifier(
            @Value("${oauth.google.client-ids:}") String googleClientIds,
            @Value("${oauth.apple.audiences:com.wjdgur050700.mystreak}") String appleAudiences
    ) throws Exception {
        this.googleAudiences = splitCsv(googleClientIds);
        this.appleAudiences = splitCsv(appleAudiences);
        this.googleProcessor = processor("https://www.googleapis.com/oauth2/v3/certs", JWSAlgorithm.RS256);
        this.appleProcessor = processor("https://appleid.apple.com/auth/keys", JWSAlgorithm.ES256);
    }

    public OidcUser verifyGoogle(String idToken) {
        if (googleAudiences.isEmpty()) {
            throw new AuthException(HttpStatus.INTERNAL_SERVER_ERROR, "구글 로그인 설정이 필요합니다.");
        }
        JWTClaimsSet claims = process(googleProcessor, idToken);
        validate(claims, "https://accounts.google.com", googleAudiences);
        return toUser(claims);
    }

    public OidcUser verifyApple(String idToken) {
        if (appleAudiences.isEmpty()) {
            throw new AuthException(HttpStatus.INTERNAL_SERVER_ERROR, "Apple 로그인 설정이 필요합니다.");
        }
        JWTClaimsSet claims = process(appleProcessor, idToken);
        validate(claims, "https://appleid.apple.com", appleAudiences);
        return toUser(claims);
    }

    private ConfigurableJWTProcessor<SecurityContext> processor(String jwksUrl, JWSAlgorithm algorithm) throws Exception {
        ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        JWSKeySelector<SecurityContext> selector = new JWSVerificationKeySelector<>(
                algorithm,
                new RemoteJWKSet<>(URI.create(jwksUrl).toURL())
        );
        processor.setJWSKeySelector(selector);
        return processor;
    }

    private JWTClaimsSet process(ConfigurableJWTProcessor<SecurityContext> processor, String idToken) {
        try {
            return processor.process(idToken, null);
        } catch (Exception e) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "소셜 로그인 토큰이 올바르지 않아요.");
        }
    }

    private void validate(JWTClaimsSet claims, String issuer, List<String> audiences) {
        if (!issuer.equals(claims.getIssuer())) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "소셜 로그인 발급자가 올바르지 않아요.");
        }
        if (claims.getAudience().stream().noneMatch(audiences::contains)) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "소셜 로그인 대상 앱이 올바르지 않아요.");
        }
        Date expiration = claims.getExpirationTime();
        if (expiration == null || expiration.before(new Date())) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "소셜 로그인 토큰이 만료됐어요.");
        }
    }

    private OidcUser toUser(JWTClaimsSet claims) {
        return new OidcUser(
                claims.getSubject(),
                stringClaim(claims, "email"),
                stringClaim(claims, "name")
        );
    }

    private String stringClaim(JWTClaimsSet claims, String key) {
        Object value = claims.getClaim(key);
        return value == null ? null : String.valueOf(value);
    }

    private List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split(",")).stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    public record OidcUser(String subject, String email, String name) {
    }
}
