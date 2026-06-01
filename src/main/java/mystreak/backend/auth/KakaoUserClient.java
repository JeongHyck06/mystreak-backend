package mystreak.backend.auth;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * 카카오 OAuth 연동 클라이언트.
 * - 인가 코드 -> 액세스 토큰 교환(kauth.kakao.com, client_secret 사용)
 * - 액세스 토큰 -> 사용자 정보 조회/검증(kapi.kakao.com)
 */
@Component
public class KakaoUserClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient authClient;
    private final RestClient apiClient;
    private final String restApiKey;
    private final String clientSecret;

    public KakaoUserClient(
            @Value("${kakao.auth-url:https://kauth.kakao.com}") String authUrl,
            @Value("${kakao.api-url:https://kapi.kakao.com}") String apiUrl,
            @Value("${kakao.rest-api-key:}") String restApiKey,
            @Value("${kakao.client-secret:}") String clientSecret
    ) {
        this.authClient = RestClient.builder().baseUrl(authUrl).build();
        this.apiClient = RestClient.builder().baseUrl(apiUrl).build();
        this.restApiKey = restApiKey;
        this.clientSecret = clientSecret;
    }

    public String exchangeCodeForToken(String code, String redirectUri) {
        if (restApiKey == null || restApiKey.isBlank()) {
            throw new AuthException(HttpStatus.INTERNAL_SERVER_ERROR, "카카오 REST API 키가 설정되지 않았어요");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", restApiKey);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }

        Map<String, Object> body;
        try {
            body = authClient.post()
                    .uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(MAP_TYPE);
        } catch (Exception e) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "카카오 토큰 교환에 실패했어요");
        }
        if (body == null || body.get("access_token") == null) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "카카오 토큰 교환에 실패했어요");
        }
        return String.valueOf(body.get("access_token"));
    }

    public KakaoUser fetchUser(String kakaoAccessToken) {
        Map<String, Object> body;
        try {
            body = apiClient.get()
                    .uri("/v2/user/me")
                    .header("Authorization", "Bearer " + kakaoAccessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(MAP_TYPE);
        } catch (Exception e) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "카카오 토큰 검증에 실패했어요");
        }
        if (body == null || body.get("id") == null) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "카카오 사용자 정보를 가져오지 못했어요");
        }

        String kakaoId = String.valueOf(body.get("id"));
        String nickname = null;
        String email = null;

        Object kakaoAccountObj = body.get("kakao_account");
        if (kakaoAccountObj instanceof Map<?, ?> account) {
            Object emailObj = account.get("email");
            if (emailObj != null) {
                email = String.valueOf(emailObj);
            }
            Object profileObj = account.get("profile");
            if (profileObj instanceof Map<?, ?> profile && profile.get("nickname") != null) {
                nickname = String.valueOf(profile.get("nickname"));
            }
        }
        if (nickname == null) {
            Object propsObj = body.get("properties");
            if (propsObj instanceof Map<?, ?> props && props.get("nickname") != null) {
                nickname = String.valueOf(props.get("nickname"));
            }
        }

        return new KakaoUser(kakaoId, nickname, email);
    }

    public record KakaoUser(String id, String nickname, String email) {
    }
}
