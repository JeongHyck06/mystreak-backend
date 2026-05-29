package mystreak.backend.checkin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCheckInRequest(
        @NotBlank @Size(max = 60) String text
) {
}
