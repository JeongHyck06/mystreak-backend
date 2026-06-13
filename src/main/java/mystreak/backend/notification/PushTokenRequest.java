package mystreak.backend.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PushTokenRequest(
        @NotBlank String token,
        @NotBlank @Pattern(regexp = "ios|android") String platform
) {
}
