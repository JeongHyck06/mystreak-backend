package mystreak.backend.checkin;

public record CommentResponse(
        String id,
        String checkInId,
        String authorId,
        String author,
        String text,
        String meta,
        boolean mine
) {
}
