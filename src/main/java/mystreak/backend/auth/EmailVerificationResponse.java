package mystreak.backend.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EmailVerificationResponse(
        boolean verified,
        @JsonProperty("expires_at") Long expiresAt
) {
}
