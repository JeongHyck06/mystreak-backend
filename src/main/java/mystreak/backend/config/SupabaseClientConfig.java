package mystreak.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class SupabaseClientConfig {

    @Bean
    RestClient supabaseAuthClient(SupabaseProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.authBaseUrl())
                .defaultHeader("apikey", properties.anonKey())
                .build();
    }
}
