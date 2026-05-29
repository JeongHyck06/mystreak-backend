package mystreak.backend.checkin;

public record CheckInResponse(
        String id,
        String podId,
        String author,
        String meta,
        String text,
        String mediaUrl,
        int likes,
        int comments,
        boolean checkedByMe
) {
}
