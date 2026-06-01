package mystreak.backend.checkin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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

@WebMvcTest(CheckInController.class)
@Import(GlobalExceptionHandler.class)
class CheckInControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private CheckInService checkInService;

    @MockitoBean
    private AuthService authService;

    @Test
    void getPodFeedReturnsFeed() throws Exception {
        when(authService.requireUserId("Bearer access-token")).thenReturn("me");
        when(checkInService.getPodFeed("me", "running"))
                .thenReturn(List.of(checkIn()));

        mockMvc.perform(get("/api/pods/running/feed")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].author").value("일정형"))
                .andExpect(jsonPath("$[0].likes").value(18));
    }

    @Test
    void createCheckInReturnsCreatedFeedItem() throws Exception {
        when(authService.requireUserId("Bearer access-token")).thenReturn("me");
        when(checkInService.createCheckIn(any(String.class), any(String.class), any(CreateCheckInRequest.class)))
                .thenReturn(new CheckInResponse("feed-3", "running", "me", "김다혜", "방금 전", "오늘 날씨가 별로여서 간단하게 했어요", null, 0, false, 0, false, 0, true));

        mockMvc.perform(post("/api/pods/running/check-ins")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCheckInRequest("오늘 날씨가 별로여서 간단하게 했어요", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("feed-3"));
    }

    @Test
    void checkReturnsReactionState() throws Exception {
        when(authService.requireUserId("Bearer access-token")).thenReturn("me");
        when(checkInService.toggleCheck("me", "feed-1"))
                .thenReturn(new CheckInResponse("feed-1", "running", "other", "일정형", "방금 전", "잠 안 와서 코딩함", null, 18, false, 19, true, 3, false));

        mockMvc.perform(post("/api/check-ins/feed-1/checks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedByMe").value(true))
                .andExpect(jsonPath("$.checks").value(19));
    }

    @Test
    void likeReturnsUpdatedState() throws Exception {
        when(authService.requireUserId("Bearer access-token")).thenReturn("me");
        when(checkInService.toggleLike("me", "feed-1"))
                .thenReturn(new CheckInResponse("feed-1", "running", "other", "일정형", "방금 전", "잠 안 와서 코딩함", null, 19, true, 0, false, 3, false));

        mockMvc.perform(post("/api/check-ins/feed-1/likes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likedByMe").value(true))
                .andExpect(jsonPath("$.likes").value(19));
    }

    @Test
    void addCommentReturnsCreatedComment() throws Exception {
        when(authService.requireUserId("Bearer access-token")).thenReturn("me");
        when(checkInService.addComment(any(String.class), any(String.class), any(CreateCommentRequest.class)))
                .thenReturn(new CommentResponse("comment-1", "feed-1", "me", "김다혜", "응원해요!", "방금 전", true));

        mockMvc.perform(post("/api/check-ins/feed-1/comments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("응원해요!"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("응원해요!"));
    }

    @Test
    void missingCheckInReturns404() throws Exception {
        when(authService.requireUserId("Bearer access-token")).thenReturn("me");
        when(checkInService.toggleCheck("me", "missing")).thenThrow(new CheckInNotFoundException("missing"));

        mockMvc.perform(post("/api/check-ins/missing/checks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private CheckInResponse checkIn() {
        return new CheckInResponse("feed-1", "running", "other", "일정형", "오늘 아침 5:24", "잠 안 와서 코딩함", null, 18, false, 5, false, 3, false);
    }
}
