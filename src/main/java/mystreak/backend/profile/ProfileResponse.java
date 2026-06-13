package mystreak.backend.profile;

public record ProfileResponse(
        String id,
        String name,
        String handle,
        String email,
        String bio,
        String avatarUrl,
        int currentStreak,
        int bestStreak,
        int totalChecks,
        int trophies
) {
}
