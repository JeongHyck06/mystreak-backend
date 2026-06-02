package mystreak.backend.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import mystreak.backend.config.OpenApiConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final String kakaoRedirectUri;
    private final String defaultAppReturnUrl;

    public AuthController(
            AuthService authService,
            EmailVerificationService emailVerificationService,
            @Value("${kakao.redirect-uri:https://mystreak.duckdns.org/api/auth/kakao/callback}") String kakaoRedirectUri,
            @Value("${kakao.app-return-url:mystreak://oauth}") String defaultAppReturnUrl
    ) {
        this.authService = authService;
        this.emailVerificationService = emailVerificationService;
        this.kakaoRedirectUri = kakaoRedirectUri;
        this.defaultAppReturnUrl = defaultAppReturnUrl;
    }

    @Operation(summary = "이메일과 비밀번호로 회원가입합니다")
    @PostMapping("/signup")
    public AuthResponse signUp(@Valid @RequestBody SignUpRequest request) {
        return authService.signUp(request);
    }

    @Operation(summary = "이메일과 비밀번호로 로그인합니다")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody SignInRequest request) {
        return authService.signIn(request);
    }

    @Operation(summary = "카카오 인가 코드로 로그인/회원가입합니다")
    @PostMapping("/kakao")
    public AuthResponse kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
        return authService.kakaoLogin(request.code(), request.redirectUri());
    }

    @Operation(summary = "Google SMTP로 이메일 인증 코드를 전송합니다")
    @PostMapping("/email/send-code")
    public EmailVerificationResponse sendEmailVerificationCode(@Valid @RequestBody EmailVerificationSendRequest request) {
        return emailVerificationService.sendCode(request);
    }

    @Operation(summary = "이메일 인증 코드를 검증합니다")
    @PostMapping("/email/verify")
    public EmailVerificationResponse verifyEmailCode(@Valid @RequestBody EmailVerificationConfirmRequest request) {
        return emailVerificationService.confirmCode(request);
    }

    /**
     * 카카오 OAuth 리다이렉트 콜백. 카카오는 http(s) redirect_uri 만 허용하므로 백엔드가 콜백을 받아
     * 코드 -> 토큰 교환 후 세션을 발급하고, state 로 전달받은 앱 딥링크로 토큰을 실어 다시 리다이렉트한다.
     */
    @Operation(summary = "카카오 OAuth 콜백 (앱 딥링크로 리다이렉트)")
    @GetMapping("/kakao/callback")
    public ResponseEntity<Void> kakaoCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription
    ) {
        String returnUrl = (state != null && !state.isBlank()) ? state : defaultAppReturnUrl;

        if (error != null && !error.isBlank()) {
            return redirect(appendQuery(returnUrl, "error", error));
        }
        if (code == null || code.isBlank()) {
            return redirect(appendQuery(returnUrl, "error", "missing_code"));
        }

        try {
            AuthResponse auth = authService.kakaoLogin(code, kakaoRedirectUri);
            String target = returnUrl
                    + (returnUrl.contains("?") ? "&" : "?")
                    + "access_token=" + enc(auth.accessToken())
                    + "&refresh_token=" + enc(auth.refreshToken())
                    + "&expires_at=" + (auth.expiresAt() != null ? auth.expiresAt() : 0L);
            return redirect(target);
        } catch (Exception e) {
            return redirect(appendQuery(returnUrl, "error", "login_failed"));
        }
    }

    @Operation(summary = "리프레시 토큰으로 액세스 토큰을 갱신합니다")
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @Operation(
            summary = "현재 사용자를 로그아웃합니다",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    )
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authService.logout(authorization);
    }

    @Operation(
            summary = "현재 MySQL 인증 사용자 정보를 조회합니다",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    )
    @GetMapping("/me")
    public Map<String, Object> me(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return authService.me(authorization);
    }

    private static ResponseEntity<Void> redirect(String url) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    private static String appendQuery(String url, String key, String value) {
        return url + (url.contains("?") ? "&" : "?") + key + "=" + enc(value);
    }

    private static String enc(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
