package mystreak.backend.pod;

public record InviteResponse(
        String podId,
        String handle,
        String status,
        String inviteLink
) {
}
