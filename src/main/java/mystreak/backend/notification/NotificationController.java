package mystreak.backend.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import jakarta.validation.Valid;
import mystreak.backend.auth.AuthService;
import mystreak.backend.config.OpenApiConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthService authService;

    public NotificationController(NotificationService notificationService, AuthService authService) {
        this.notificationService = notificationService;
        this.authService = authService;
    }

    @Operation(summary = "내 알림 목록을 조회합니다")
    @GetMapping
    public List<NotificationResponse> getNotifications(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(required = false) String type
    ) {
        String profileId = authService.requireUserId(authorization);
        return notificationService.getNotifications(profileId, type);
    }

    @Operation(summary = "모든 알림을 읽음 처리합니다")
    @PatchMapping("/read-all")
    public List<NotificationResponse> markAllRead(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String profileId = authService.requireUserId(authorization);
        return notificationService.markAllRead(profileId);
    }

    @Operation(summary = "푸시 알림 토큰을 등록합니다")
    @PostMapping("/push-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registerPushToken(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody PushTokenRequest request
    ) {
        String profileId = authService.requireUserId(authorization);
        notificationService.registerPushToken(profileId, request.token(), request.platform());
    }
}
