package mystreak.backend.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 30) String name,
        @NotBlank @Pattern(regexp = "^@[a-zA-Z0-9._]{3,30}$") String handle,
        @Size(max = 50) String bio
) {
}
