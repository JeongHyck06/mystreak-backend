package mystreak.backend.auth;

import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class SupabaseAuthService {

    private final RestClient supabaseAuthClient;

    public SupabaseAuthService(RestClient supabaseAuthClient) {
        this.supabaseAuthClient = supabaseAuthClient;
    }

    public SupabaseAuthResponse signUp(SignUpRequest request) {
        return postAuth("/signup", Map.of(
                "email", request.email(),
                "password", request.password()
        ));
    }

    public SupabaseAuthResponse signIn(SignInRequest request) {
        return postAuth("/token?grant_type=password", Map.of(
                "email", request.email(),
                "password", request.password()
        ));
    }

    public SupabaseAuthResponse refresh(RefreshTokenRequest request) {
        return postAuth("/token?grant_type=refresh_token", Map.of(
                "refresh_token", request.refreshToken()
        ));
    }

    public void logout(String bearerToken) {
        try {
            supabaseAuthClient.post()
                    .uri("/logout")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw toAuthException(exception);
        }
    }

    public Map<String, Object> me(String bearerToken) {
        try {
            return supabaseAuthClient.get()
                    .uri("/user")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (RestClientResponseException exception) {
            throw toAuthException(exception);
        }
    }

    private SupabaseAuthResponse postAuth(String uri, Map<String, String> body) {
        try {
            return supabaseAuthClient.post()
                    .uri(uri)
                    .body(body)
                    .retrieve()
                    .body(SupabaseAuthResponse.class);
        } catch (RestClientResponseException exception) {
            throw toAuthException(exception);
        }
    }

    private SupabaseAuthException toAuthException(RestClientResponseException exception) {
        HttpStatusCode statusCode = exception.getStatusCode();
        String message = exception.getResponseBodyAsString();
        if (message == null || message.isBlank()) {
            message = "Supabase authentication request failed";
        }
        return new SupabaseAuthException(statusCode, message);
    }
}
