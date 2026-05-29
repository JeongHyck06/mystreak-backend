package mystreak.backend.notification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final Map<String, NotificationResponse> notifications = new LinkedHashMap<>();

    public List<NotificationResponse> getNotifications(String type) {
        if (type == null || type.isBlank() || type.equals("all")) {
            return new ArrayList<>(notifications.values());
        }

        return notifications.values()
                .stream()
                .filter(notification -> notification.type().equals(type))
                .toList();
    }

    public List<NotificationResponse> markAllRead() {
        notifications.replaceAll((id, notification) -> new NotificationResponse(
                notification.id(),
                notification.title(),
                notification.body(),
                notification.meta(),
                notification.type(),
                notification.urgent(),
                true
        ));
        return getNotifications(null);
    }
}
