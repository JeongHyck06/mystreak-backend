package mystreak.backend.stats;

import java.util.List;

public record StatsResponse(
        int currentStreak,
        int bestStreak,
        int weeklyChecks,
        int weeklyGoal,
        int totalChecks,
        int activePods,
        int monthlyCompletionRate,
        int checkedDaysInMonth,
        int month,
        int year,
        List<Integer> heatmap,
        String recentTrophy
) {
}
