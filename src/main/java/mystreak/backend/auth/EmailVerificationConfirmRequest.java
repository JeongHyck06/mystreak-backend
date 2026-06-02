package mystreak.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmailVerificationConfirmRequest(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "^\\d{6}$") String code,
        @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_-]{1,40}$") String purpose
) {
}
