package mystreak.backend.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.Map;
import mystreak.backend.config.OpenApiConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SupabaseAuthService authService;

    public AuthController(SupabaseAuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "이메일과 비밀번호로 회원가입합니다")
    @PostMapping("/signup")
    public SupabaseAuthResponse signUp(@Valid @RequestBody SignUpRequest request) {
        return authService.signUp(request);
    }

    @Operation(summary = "이메일과 비밀번호로 로그인합니다")
    @PostMapping("/login")
    public SupabaseAuthResponse login(@Valid @RequestBody SignInRequest request) {
        return authService.signIn(request);
    }

    @Operation(summary = "리프레시 토큰으로 액세스 토큰을 갱신합니다")
    @PostMapping("/refresh")
    public SupabaseAuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @Operation(
            summary = "현재 사용자를 로그아웃합니다",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    )
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authService.logout(requireBearerToken(authorization));
    }

    @Operation(
            summary = "현재 Supabase 인증 사용자 정보를 조회합니다",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    )
    @GetMapping("/me")
    public Map<String, Object> me(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return authService.me(requireBearerToken(authorization));
    }

    private String requireBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token is required");
        }
        return authorization;
    }
}
