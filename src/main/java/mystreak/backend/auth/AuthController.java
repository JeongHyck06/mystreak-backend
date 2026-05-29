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

    @Operation(summary = "Create a Supabase auth user")
    @PostMapping("/signup")
    public SupabaseAuthResponse signUp(@Valid @RequestBody SignUpRequest request) {
        return authService.signUp(request);
    }

    @Operation(summary = "Sign in with email and password")
    @PostMapping("/login")
    public SupabaseAuthResponse login(@Valid @RequestBody SignInRequest request) {
        return authService.signIn(request);
    }

    @Operation(summary = "Refresh an access token")
    @PostMapping("/refresh")
    public SupabaseAuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @Operation(
            summary = "Sign out the current user",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    )
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authService.logout(requireBearerToken(authorization));
    }

    @Operation(
            summary = "Get the current Supabase auth user",
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
