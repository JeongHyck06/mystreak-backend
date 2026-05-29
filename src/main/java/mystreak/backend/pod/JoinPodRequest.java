package mystreak.backend.pod;

import jakarta.validation.constraints.NotBlank;

public record JoinPodRequest(
        @NotBlank String inviteCode
) {
}
