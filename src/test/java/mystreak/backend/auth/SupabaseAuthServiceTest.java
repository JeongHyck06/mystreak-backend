package mystreak.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SupabaseAuthServiceTest {

    private final RestClient.Builder builder = RestClient.builder()
            .baseUrl("https://project.supabase.co/auth/v1")
            .defaultHeader("apikey", "test-anon-key");

    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private final SupabaseAuthService authService = new SupabaseAuthService(builder.build());

    @Test
    void signInPostsPasswordGrantToSupabase() {
        server.expect(requestTo("https://project.supabase.co/auth/v1/token?grant_type=password"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", "test-anon-key"))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.password").value("password1"))
                .andRespond(withSuccess("""
                        {
                          "access_token": "access-token",
                          "refresh_token": "refresh-token",
                          "expires_in": 3600,
                          "token_type": "bearer",
                          "user": { "id": "user-id", "email": "user@example.com" }
                        }
                        """, MediaType.APPLICATION_JSON));

        SupabaseAuthResponse response = authService.signIn(new SignInRequest("user@example.com", "password1"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.user()).containsEntry("email", "user@example.com");
        server.verify();
    }

    @Test
    void meForwardsBearerTokenToSupabase() {
        server.expect(requestTo("https://project.supabase.co/auth/v1/user"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andRespond(withSuccess("""
                        { "id": "user-id", "email": "user@example.com" }
                        """, MediaType.APPLICATION_JSON));

        assertThat(authService.me("Bearer access-token"))
                .containsEntry("id", "user-id")
                .containsEntry("email", "user@example.com");
        server.verify();
    }

    @Test
    void upstreamAuthErrorsAreMapped() {
        server.expect(requestTo("https://project.supabase.co/auth/v1/token?grant_type=password"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("Invalid login credentials"));

        assertThatThrownBy(() -> authService.signIn(new SignInRequest("user@example.com", "password1")))
                .isInstanceOf(SupabaseAuthException.class)
                .hasMessage("Invalid login credentials")
                .extracting("statusCode")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        server.verify();
    }
}
