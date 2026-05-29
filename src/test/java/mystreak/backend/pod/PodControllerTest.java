package mystreak.backend.pod;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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

@WebMvcTest(PodController.class)
@Import(GlobalExceptionHandler.class)
class PodControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private PodService podService;

    @MockitoBean
    private AuthService authService;

    @Test
    void getMyPodsReturnsPods() throws Exception {
        when(authService.requireUserId("Bearer access-token")).thenReturn("me");
        when(podService.getMyPods("me")).thenReturn(List.of(pod()));

        mockMvc.perform(get("/api/pods")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("running"))
                .andExpect(jsonPath("$[0].certifiedToday").value(6));
    }

    @Test
    void createPodReturnsCreatedPod() throws Exception {
        when(authService.requireUserId("Bearer access-token")).thenReturn("me");
        when(podService.createPod(any(String.class), any(CreatePodRequest.class))).thenReturn(pod());

        CreatePodRequest request = new CreatePodRequest("새벽 5시 러닝 크루", "아침 5시, 함께 달립니다.", 8, "운동 · 사진 인증", List.of("#러닝"));

        mockMvc.perform(post("/api/pods")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("running"));
    }

    @Test
    void joinPodUsesInviteCode() throws Exception {
        when(authService.requireUserId("Bearer access-token")).thenReturn("me");
        when(podService.joinPod(any(String.class), any(JoinPodRequest.class))).thenReturn(pod());

        mockMvc.perform(post("/api/pods/join")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JoinPodRequest("ABC123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inviteCode").value("ABC123"));
    }

    @Test
    void inviteMemberReturnsInviteStatus() throws Exception {
        when(podService.inviteMember("running", new InviteMemberRequest("@friend.id")))
                .thenReturn(new InviteResponse("running", "@friend.id", "sent", "mystreak.app/pod/ABC123"));

        mockMvc.perform(post("/api/pods/running/invites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InviteMemberRequest("@friend.id"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("sent"));
    }

    @Test
    void getMembersReturnsPodMembers() throws Exception {
        when(podService.getMembers("running"))
                .thenReturn(List.of(new PodMemberResponse("me", "김다혜", "@doitall", 12, true, "나")));

        mockMvc.perform(get("/api/pods/running/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].checkedInToday").value(true));
    }

    @Test
    void leavePodReturnsNoContent() throws Exception {
        when(authService.requireUserId("Bearer access-token")).thenReturn("me");

        mockMvc.perform(delete("/api/pods/running/members/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isNoContent());

        verify(podService).leavePod("me", "running");
    }

    @Test
    void podNotFoundReturns404() throws Exception {
        when(podService.getPod("missing")).thenThrow(new PodNotFoundException("missing"));

        mockMvc.perform(get("/api/pods/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private PodResponse pod() {
        return new PodResponse("running", "새벽 5시 러닝 크루", "아침 5시, 함께 달립니다.", 248, 6, 8, 12, "운동 · 사진 인증", List.of("#러닝", "#새벽기상", "#운동"), false, "ABC123");
    }
}
