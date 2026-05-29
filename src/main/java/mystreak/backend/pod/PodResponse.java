package mystreak.backend.pod;

import java.util.List;

public record PodResponse(
        String id,
        String name,
        String description,
        int memberCount,
        int certifiedToday,
        int maxMembers,
        int streak,
        String tagLine,
        List<String> tags,
        boolean needsCheckIn,
        String inviteCode
) {
}
