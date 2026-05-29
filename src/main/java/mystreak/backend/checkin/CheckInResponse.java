package mystreak.backend.checkin;

public record CheckInResponse(
        String id,
        String podId,
        String authorId,
        String author,
        String meta,
        String text,
        String mediaUrl,
        int likes,
        boolean likedByMe,
        int checks,
        boolean checkedByMe,
        int comments,
        boolean mine
) {
}
