package mystreak.backend.pod;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record InviteMemberRequest(
        @NotBlank @Pattern(regexp = "^@[a-zA-Z0-9._]{3,30}$") String handle
) {
}
