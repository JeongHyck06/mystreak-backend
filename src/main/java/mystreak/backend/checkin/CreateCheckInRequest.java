package mystreak.backend.checkin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCheckInRequest(
        @NotBlank @Size(max = 60) String text,
        String mediaUrl
) {
}
