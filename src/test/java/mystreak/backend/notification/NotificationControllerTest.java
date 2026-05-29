package mystreak.backend.notification;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void getNotificationsReturnsNotifications() throws Exception {
        when(notificationService.getNotifications("check"))
                .thenReturn(List.of(notification(false)));

        mockMvc.perform(get("/api/notifications")
                        .param("type", "check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("지수님이 내 인증을 체크했어요"))
                .andExpect(jsonPath("$[0].read").value(false));
    }

    @Test
    void markAllReadReturnsReadNotifications() throws Exception {
        when(notificationService.markAllRead())
                .thenReturn(List.of(notification(true)));

        mockMvc.perform(patch("/api/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].read").value(true));
    }

    private NotificationResponse notification(boolean read) {
        return new NotificationResponse("n2", "지수님이 내 인증을 체크했어요", "새벽 5시 러닝 크루 · 이번 주 6번째 인증 완료", "1시간 전", "check", false, read);
    }
}
