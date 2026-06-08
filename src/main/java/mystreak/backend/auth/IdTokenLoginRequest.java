package mystreak.backend.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record IdTokenLoginRequest(
        @JsonProperty("id_token") @NotBlank String idToken,
        @JsonProperty("full_name") String fullName
) {
}
