package mystreak.backend.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import mystreak.backend.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AuthService authService;

    @Test
    void loginReturnsMysqlTokenResponse() throws Exception {
        Map<String, Object> user = Map.of(
                "id", "user-id",
                "email", "user@example.com"
        );

        when(authService.signIn(any(SignInRequest.class)))
                .thenReturn(new AuthResponse("access-token", "refresh-token", 3600L, 123456789L, "bearer", user));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignInRequest("user@example.com", "password1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("access-token"))
                .andExpect(jsonPath("$.refresh_token").value("refresh-token"))
                .andExpect(jsonPath("$.user.email").value("user@example.com"));
    }

    @Test
    void signupRejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignUpRequest("not-an-email", "password1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void meRequiresBearerToken() throws Exception {
        doThrow(new AuthException(HttpStatus.UNAUTHORIZED, "Bearer token is required"))
                .when(authService)
                .me(null);

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Bearer token is required"));
    }

    @Test
    void meReturnsMysqlUser() throws Exception {
        Map<String, Object> user = Map.of(
                "id", "user-id",
                "email", "user@example.com"
        );

        when(authService.me("Bearer access-token")).thenReturn(user);

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-id"))
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    @Test
    void logoutForwardsBearerToken() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isNoContent());

        verify(authService).logout("Bearer access-token");
    }

    @Test
    void authErrorsKeepStatus() throws Exception {
        doThrow(new AuthException(HttpStatus.UNAUTHORIZED, "Invalid login credentials"))
                .when(authService)
                .signIn(any(SignInRequest.class));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignInRequest("user@example.com", "password1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid login credentials"));
    }
}
