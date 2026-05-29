package mystreak.backend.checkin;

public record CheckReactionResponse(
        String checkInId,
        boolean checkedByMe,
        int likes
) {
}
