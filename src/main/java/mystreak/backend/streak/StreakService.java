package mystreak.backend.streak;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증(체크인) 기록을 기반으로 스트릭과 월별 통계를 다시 계산해 저장합니다.
 * 인증 등록/삭제 시 호출되어 profiles, pod_members, pods, user_stats 를 갱신합니다.
 */
@Service
public class StreakService {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final JdbcClient jdbcClient;

    public StreakService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public void recalculateProfile(String profileId) {
        List<LocalDate> dates = checkInDates(profileId, null);
        LocalDate today = LocalDate.now(ZONE);
        Set<LocalDate> daySet = new HashSet<>(dates);

        int currentStreak = currentStreak(daySet, today);
        int bestStreak = bestStreak(dates);
        int totalChecks = dates.size();

        jdbcClient.sql("""
                        UPDATE profiles
                        SET current_streak = :current,
                            best_streak = GREATEST(best_streak, :best),
                            total_checks = :total
                        WHERE id = :id
                        """)
                .param("id", profileId)
                .param("current", currentStreak)
                .param("best", bestStreak)
                .param("total", totalChecks)
                .update();

        upsertMonthlyStats(profileId, today, daySet, dates, currentStreak, bestStreak, totalChecks);
    }

    @Transactional
    public void recalculatePod(String podId) {
        LocalDate today = LocalDate.now(ZONE);

        List<String> memberIds = jdbcClient.sql("SELECT profile_id FROM pod_members WHERE pod_id = :podId")
                .param("podId", podId)
                .query(String.class)
                .list();

        Set<LocalDate> podDays = new HashSet<>();
        Set<String> authorsToday = new HashSet<>();
        for (String memberId : memberIds) {
            List<LocalDate> memberDates = checkInDates(memberId, podId);
            Set<LocalDate> memberDaySet = new HashSet<>(memberDates);
            podDays.addAll(memberDaySet);

            boolean checkedToday = memberDaySet.contains(today);
            if (checkedToday) {
                authorsToday.add(memberId);
            }
            jdbcClient.sql("""
                            UPDATE pod_members
                            SET streak = :streak, checked_in_today = :checkedToday
                            WHERE pod_id = :podId AND profile_id = :profileId
                            """)
                    .param("podId", podId)
                    .param("profileId", memberId)
                    .param("streak", currentStreak(memberDaySet, today))
                    .param("checkedToday", checkedToday)
                    .update();
        }

        int podStreak = currentStreak(podDays, today);
        jdbcClient.sql("""
                        UPDATE pods
                        SET certified_today = :certified,
                            streak = :streak,
                            needs_check_in = :needsCheckIn
                        WHERE id = :id
                        """)
                .param("id", podId)
                .param("certified", authorsToday.size())
                .param("streak", podStreak)
                .param("needsCheckIn", authorsToday.size() < memberIds.size())
                .update();
    }

    private void upsertMonthlyStats(
            String profileId,
            LocalDate today,
            Set<LocalDate> daySet,
            List<LocalDate> dates,
            int currentStreak,
            int bestStreak,
            int totalChecks
    ) {
        YearMonth month = YearMonth.from(today);
        int lengthOfMonth = month.lengthOfMonth();

        StringBuilder heatmap = new StringBuilder();
        int checkedDaysInMonth = 0;
        for (int day = 1; day <= lengthOfMonth; day++) {
            LocalDate date = month.atDay(day);
            long count = dates.stream().filter(date::equals).count();
            if (count > 0) {
                checkedDaysInMonth++;
            }
            int level = (int) Math.min(count, 4);
            if (day > 1) {
                heatmap.append(",");
            }
            heatmap.append(level);
        }

        LocalDate weekStart = today.minusDays(6);
        int weeklyChecks = (int) daySet.stream()
                .filter(date -> !date.isBefore(weekStart) && !date.isAfter(today))
                .count();

        int activePods = jdbcClient.sql("SELECT COUNT(*) FROM pod_members WHERE profile_id = :id")
                .param("id", profileId)
                .query(Integer.class)
                .single();

        int monthlyCompletionRate = lengthOfMonth == 0
                ? 0
                : Math.round((checkedDaysInMonth * 100f) / lengthOfMonth);

        jdbcClient.sql("""
                        INSERT INTO user_stats (
                            profile_id, stat_year, stat_month, current_streak, best_streak,
                            weekly_checks, weekly_goal, total_checks, active_pods,
                            monthly_completion_rate, checked_days_in_month, heatmap
                        ) VALUES (
                            :profileId, :year, :month, :current, :best,
                            :weeklyChecks, 7, :total, :activePods,
                            :rate, :checkedDays, :heatmap
                        )
                        ON DUPLICATE KEY UPDATE
                            current_streak = VALUES(current_streak),
                            best_streak = GREATEST(best_streak, VALUES(best_streak)),
                            weekly_checks = VALUES(weekly_checks),
                            total_checks = VALUES(total_checks),
                            active_pods = VALUES(active_pods),
                            monthly_completion_rate = VALUES(monthly_completion_rate),
                            checked_days_in_month = VALUES(checked_days_in_month),
                            heatmap = VALUES(heatmap)
                        """)
                .param("profileId", profileId)
                .param("year", month.getYear())
                .param("month", month.getMonthValue())
                .param("current", currentStreak)
                .param("best", bestStreak)
                .param("weeklyChecks", weeklyChecks)
                .param("total", totalChecks)
                .param("activePods", activePods)
                .param("rate", monthlyCompletionRate)
                .param("checkedDays", checkedDaysInMonth)
                .param("heatmap", heatmap.toString())
                .update();
    }

    private List<LocalDate> checkInDates(String profileId, String podId) {
        String sql = podId == null
                ? "SELECT created_at FROM check_ins WHERE author_id = :profileId"
                : "SELECT created_at FROM check_ins WHERE author_id = :profileId AND pod_id = :podId";
        var spec = jdbcClient.sql(sql).param("profileId", profileId);
        if (podId != null) {
            spec = spec.param("podId", podId);
        }
        return spec.query(Timestamp.class)
                .list()
                .stream()
                .filter(timestamp -> timestamp != null)
                .map(timestamp -> timestamp.toInstant().atZone(ZONE).toLocalDate())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private int currentStreak(Set<LocalDate> days, LocalDate today) {
        LocalDate cursor;
        if (days.contains(today)) {
            cursor = today;
        } else if (days.contains(today.minusDays(1))) {
            cursor = today.minusDays(1);
        } else {
            return 0;
        }

        int streak = 0;
        while (days.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private int bestStreak(List<LocalDate> sortedDates) {
        if (sortedDates.isEmpty()) {
            return 0;
        }
        int best = 1;
        int run = 1;
        for (int i = 1; i < sortedDates.size(); i++) {
            if (sortedDates.get(i - 1).plusDays(1).equals(sortedDates.get(i))) {
                run++;
            } else {
                run = 1;
            }
            best = Math.max(best, run);
        }
        return best;
    }
}
