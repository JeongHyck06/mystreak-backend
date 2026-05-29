package mystreak.backend.profile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import mystreak.backend.auth.AuthService;
import mystreak.backend.config.OpenApiConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class ProfileController {

    private final ProfileService profileService;
    private final AuthService authService;

    public ProfileController(ProfileService profileService, AuthService authService) {
        this.profileService = profileService;
        this.authService = authService;
    }

    @Operation(summary = "내 프로필을 조회합니다")
    @GetMapping("/me")
    public ProfileResponse getMyProfile(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return profileService.getMyProfile(authService.requireUserId(authorization));
    }

    @Operation(summary = "내 프로필을 수정합니다")
    @PatchMapping("/me")
    public ProfileResponse updateMyProfile(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return profileService.updateMyProfile(authService.requireUserId(authorization), request);
    }
}
