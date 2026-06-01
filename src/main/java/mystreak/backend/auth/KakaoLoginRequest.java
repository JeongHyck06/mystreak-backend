package mystreak.backend.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * 앱이 카카오 OAuth 로 받은 인가 코드(code)와 그때 사용한 redirect_uri 를 전달한다.
 * 토큰 교환(코드 -> 액세스 토큰)은 client_secret 보호를 위해 백엔드에서 수행한다.
 */
public record KakaoLoginRequest(
        @NotBlank String code,
        @JsonProperty("redirect_uri") @NotBlank String redirectUri
) {
}
