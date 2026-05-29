package mystreak.backend.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import mystreak.backend.config.OpenApiConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "내 알림 목록을 조회합니다")
    @GetMapping
    public List<NotificationResponse> getNotifications(@RequestParam(required = false) String type) {
        return notificationService.getNotifications(type);
    }

    @Operation(summary = "모든 알림을 읽음 처리합니다")
    @PatchMapping("/read-all")
    public List<NotificationResponse> markAllRead() {
        return notificationService.markAllRead();
    }
}
