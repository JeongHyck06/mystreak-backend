package mystreak.backend.profile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import mystreak.backend.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProfileController.class)
@Import(GlobalExceptionHandler.class)
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ProfileService profileService;

    @Test
    void getMyProfileReturnsProfile() throws Exception {
        when(profileService.getMyProfile()).thenReturn(profile());

        mockMvc.perform(get("/api/profile/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("김다혜"))
                .andExpect(jsonPath("$.handle").value("@doitall"))
                .andExpect(jsonPath("$.currentStreak").value(27));
    }

    @Test
    void updateMyProfileReturnsUpdatedProfile() throws Exception {
        when(profileService.updateMyProfile(any(UpdateProfileRequest.class)))
                .thenReturn(new ProfileResponse("me", "김다혜", "@new.handle", "kdh@example.com", "매일 조금씩 더 나아지는 중", 27, 42, 146, 38));

        mockMvc.perform(patch("/api/profile/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProfileRequest("김다혜", "@new.handle", "매일 조금씩 더 나아지는 중"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle").value("@new.handle"))
                .andExpect(jsonPath("$.bio").value("매일 조금씩 더 나아지는 중"));
    }

    @Test
    void updateMyProfileRejectsInvalidHandle() throws Exception {
        mockMvc.perform(patch("/api/profile/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProfileRequest("김다혜", "invalid", "bio"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private ProfileResponse profile() {
        return new ProfileResponse("me", "김다혜", "@doitall", "kdh@example.com", "오이, 당근 입에 안 댑니다", 27, 42, 146, 38);
    }
}
