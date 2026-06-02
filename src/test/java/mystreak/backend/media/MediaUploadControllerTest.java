package mystreak.backend.media;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import mystreak.backend.auth.AuthService;
import mystreak.backend.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MediaUploadController.class)
@Import(GlobalExceptionHandler.class)
class MediaUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private MediaUploadService mediaUploadService;

    @MockitoBean
    private AuthService authService;

    @Test
    void createUploadReturnsS3UploadUrl() throws Exception {
        CreateMediaUploadRequest request = new CreateMediaUploadRequest("proof.jpg", "image/jpeg", UploadMediaType.IMAGE, null);

        when(authService.requireUserId("Bearer access-token")).thenReturn("me");
        when(mediaUploadService.createUpload("me", request))
                .thenReturn(new MediaUploadResponse(
                        "https://bucket.s3.ap-northeast-2.amazonaws.com/check-ins/me/proof.jpg?signature=test",
                        "https://bucket.s3.ap-northeast-2.amazonaws.com/check-ins/me/proof.jpg",
                        "check-ins/me/proof.jpg",
                        600
                ));

        mockMvc.perform(post("/api/media/uploads")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadUrl").value("https://bucket.s3.ap-northeast-2.amazonaws.com/check-ins/me/proof.jpg?signature=test"))
                .andExpect(jsonPath("$.mediaUrl").value("https://bucket.s3.ap-northeast-2.amazonaws.com/check-ins/me/proof.jpg"))
                .andExpect(jsonPath("$.objectKey").value("check-ins/me/proof.jpg"))
                .andExpect(jsonPath("$.expiresInSeconds").value(600));
    }
}
