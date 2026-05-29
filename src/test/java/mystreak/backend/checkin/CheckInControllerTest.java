package mystreak.backend.checkin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import mystreak.backend.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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

    @Test
    void getPodFeedReturnsFeed() throws Exception {
        when(checkInService.getPodFeed("running"))
                .thenReturn(List.of(checkIn()));

        mockMvc.perform(get("/api/pods/running/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].author").value("일정형"))
                .andExpect(jsonPath("$[0].likes").value(18));
    }

    @Test
    void createCheckInReturnsCreatedFeedItem() throws Exception {
        when(checkInService.createCheckIn(any(String.class), any(CreateCheckInRequest.class)))
                .thenReturn(new CheckInResponse("feed-3", "running", "김다혜", "방금 전 · 27일째", "오늘 날씨가 별로여서 간단하게 했어요", null, 0, 0, false));

        mockMvc.perform(post("/api/pods/running/check-ins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCheckInRequest("오늘 날씨가 별로여서 간단하게 했어요", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("feed-3"));
    }

    @Test
    void checkReturnsReactionState() throws Exception {
        when(checkInService.check("feed-1"))
                .thenReturn(new CheckReactionResponse("feed-1", true, 19));

        mockMvc.perform(post("/api/check-ins/feed-1/checks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedByMe").value(true))
                .andExpect(jsonPath("$.likes").value(19));
    }

    @Test
    void missingCheckInReturns404() throws Exception {
        when(checkInService.check("missing")).thenThrow(new CheckInNotFoundException("missing"));

        mockMvc.perform(post("/api/check-ins/missing/checks"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private CheckInResponse checkIn() {
        return new CheckInResponse("feed-1", "running", "일정형", "오늘 아침 5:24 · 12일째", "잠 안 와서 코딩함", null, 18, 3, false);
    }
}
