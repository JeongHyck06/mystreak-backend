package mystreak.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6, max = 72) String password,
        @NotBlank @Size(max = 30) String name,
        @NotBlank @Pattern(regexp = "^@?[a-zA-Z0-9._]{3,30}$") String handle
) {
}
