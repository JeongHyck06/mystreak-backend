package mystreak.backend.profile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import mystreak.backend.config.OpenApiConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Operation(summary = "Get my profile")
    @GetMapping("/me")
    public ProfileResponse getMyProfile() {
        return profileService.getMyProfile();
    }

    @Operation(summary = "Update my profile")
    @PatchMapping("/me")
    public ProfileResponse updateMyProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return profileService.updateMyProfile(request);
    }
}
