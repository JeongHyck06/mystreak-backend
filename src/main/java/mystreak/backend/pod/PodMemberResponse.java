package mystreak.backend.pod;

public record PodMemberResponse(
        String id,
        String name,
        String handle,
        int streak,
        boolean checkedInToday,
        String role
) {
}
