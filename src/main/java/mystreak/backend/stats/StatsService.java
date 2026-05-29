package mystreak.backend.stats;

import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class StatsService {

    private final JdbcClient jdbcClient;

    public StatsService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public StatsResponse getMyStats(Integer year, Integer month) {
        YearMonth now = YearMonth.now();
        YearMonth selectedMonth = YearMonth.of(
                year == null ? now.getYear() : year,
                month == null ? now.getMonthValue() : month
        );

        return jdbcClient.sql("""
                        SELECT current_streak, best_streak, weekly_checks, weekly_goal, total_checks,
                               active_pods, monthly_completion_rate, checked_days_in_month,
                               stat_month, stat_year, heatmap, recent_trophy
                        FROM user_stats
                        WHERE profile_id = :profileId
                          AND stat_year = :year
                          AND stat_month = :month
                        """)
                .param("profileId", "me")
                .param("year", selectedMonth.getYear())
                .param("month", selectedMonth.getMonthValue())
                .query((rs, rowNum) -> new StatsResponse(
                        rs.getInt("current_streak"),
                        rs.getInt("best_streak"),
                        rs.getInt("weekly_checks"),
                        rs.getInt("weekly_goal"),
                        rs.getInt("total_checks"),
                        rs.getInt("active_pods"),
                        rs.getInt("monthly_completion_rate"),
                        rs.getInt("checked_days_in_month"),
                        rs.getInt("stat_month"),
                        rs.getInt("stat_year"),
                        parseHeatmap(rs.getString("heatmap")),
                        rs.getString("recent_trophy")
                ))
                .optional()
                .orElse(new StatsResponse(0, 0, 0, 7, 0, 0, 0, 0, selectedMonth.getMonthValue(), selectedMonth.getYear(), List.of(), null));
    }

    private List<Integer> parseHeatmap(String heatmap) {
        if (heatmap == null || heatmap.isBlank()) {
            return List.of();
        }
        return Arrays.stream(heatmap.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Integer::parseInt)
                .toList();
    }
}
