package mystreak.backend.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "supabase")
public record SupabaseProperties(
        @NotBlank String url,
        @NotBlank String anonKey
) {

    public String authBaseUrl() {
        return url.replaceAll("/+$", "") + "/auth/v1";
    }
}
