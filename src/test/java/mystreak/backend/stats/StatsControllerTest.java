package mystreak.backend.stats;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import mystreak.backend.auth.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StatsController.class)
class StatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StatsService statsService;

    @MockitoBean
    private AuthService authService;

    @Test
    void getMyStatsReturnsMonthlyStats() throws Exception {
        when(authService.requireUserId("Bearer access-token")).thenReturn("me");
        when(statsService.getMyStats("me", 2026, 5))
                .thenReturn(new StatsResponse(27, 42, 6, 7, 146, 3, 87, 26, 5, 2026, List.of(0, 2, 4), "연속 3주 완주 클럽하우스"));

        mockMvc.perform(get("/api/stats/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .param("year", "2026")
                        .param("month", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreak").value(27))
                .andExpect(jsonPath("$.monthlyCompletionRate").value(87))
                .andExpect(jsonPath("$.heatmap[2]").value(4));
    }
}
