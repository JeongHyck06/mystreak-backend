package mystreak.backend.notification;

public record NotificationResponse(
        String id,
        String title,
        String body,
        String meta,
        String type,
        boolean urgent,
        boolean read
) {
}
