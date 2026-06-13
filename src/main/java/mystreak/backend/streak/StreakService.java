package mystreak.backend.streak;

import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import mystreak.backend.common.AppTime;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증(체크인) 기록을 기반으로 스트릭과 월별 통계를 다시 계산해 저장합니다.
 * 인증 등록/삭제 시 호출되어 profiles, pod_members, pods, user_stats 를 갱신합니다.
 */
@Service
public class StreakService {

    private static final int[] TROPHY_STREAK_DAYS = {30, 100, 300};

    private final JdbcClient jdbcClient;
    private final DataSource dataSource;

    public StreakService(JdbcClient jdbcClient, DataSource dataSource) {
        this.jdbcClient = jdbcClient;
        this.dataSource = dataSource;
    }

    @PostConstruct
    void ensureStreakColumns() {
        if (tableExists("user_stats") && !columnExists("user_stats", "recent_trophy")) {
            jdbcClient.sql("ALTER TABLE user_stats ADD COLUMN recent_trophy VARCHAR(120)").update();
        }
    }

    @Transactional
    public void recalculateProfile(String profileId) {
        List<LocalDate> dates = checkInDates(profileId, null);
        LocalDate today = AppTime.today();
        Set<LocalDate> daySet = new HashSet<>(dates);
        List<LocalDate> distinctDays = daySet.stream().sorted().toList();

        int currentStreak = currentStreak(daySet, today);
        int bestStreak = bestStreak(distinctDays);
        int totalChecks = dates.size();
        int trophies = trophyCount(currentStreak);

        jdbcClient.sql("""
                        UPDATE profiles
                        SET current_streak = :current,
                            best_streak = GREATEST(best_streak, :best),
                            total_checks = :total,
                            trophies = :trophies
                        WHERE id = :id
                        """)
                .param("id", profileId)
                .param("current", currentStreak)
                .param("best", bestStreak)
                .param("total", totalChecks)
                .param("trophies", trophies)
                .update();

        upsertMonthlyStats(profileId, today, daySet, dates, currentStreak, bestStreak, totalChecks);
    }

    @Transactional
    public void recalculatePod(String podId) {
        LocalDate today = AppTime.today();

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
                            monthly_completion_rate, checked_days_in_month, heatmap, recent_trophy
                        ) VALUES (
                            :profileId, :year, :month, :current, :best,
                            :weeklyChecks, 7, :total, :activePods,
                            :rate, :checkedDays, :heatmap, :recentTrophy
                        )
                        ON DUPLICATE KEY UPDATE
                            current_streak = VALUES(current_streak),
                            best_streak = GREATEST(best_streak, VALUES(best_streak)),
                            weekly_checks = VALUES(weekly_checks),
                            total_checks = VALUES(total_checks),
                            active_pods = VALUES(active_pods),
                            monthly_completion_rate = VALUES(monthly_completion_rate),
                            checked_days_in_month = VALUES(checked_days_in_month),
                            heatmap = VALUES(heatmap),
                            recent_trophy = VALUES(recent_trophy)
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
                .param("recentTrophy", recentTrophy(currentStreak))
                .update();
    }

    private int trophyCount(int currentStreak) {
        int count = 0;
        for (int day : TROPHY_STREAK_DAYS) {
            if (currentStreak >= day) {
                count++;
            }
        }
        return count;
    }

    private String recentTrophy(int currentStreak) {
        String trophy = null;
        for (int day : TROPHY_STREAK_DAYS) {
            if (currentStreak >= day) {
                trophy = "%d일 연속 달성!".formatted(day);
            }
        }
        return trophy;
    }

    /**
     * 인증으로 인정되는 글의 작성일 목록을 반환한다.
     * "인증"은 본인이 글을 올리는 것이 아니라 다른 멤버가 체크(check_in_checks)해줘야 성립하므로,
     * 다른 사람의 체크가 1개 이상 달린 글만 집계한다.
     */
    private List<LocalDate> checkInDates(String profileId, String podId) {
        String verified = " AND EXISTS (SELECT 1 FROM check_in_checks c WHERE c.check_in_id = ci.id)";
        String sql = podId == null
                ? "SELECT ci.created_at FROM check_ins ci WHERE ci.author_id = :profileId" + verified
                : "SELECT ci.created_at FROM check_ins ci WHERE ci.author_id = :profileId AND ci.pod_id = :podId" + verified;
        var spec = jdbcClient.sql(sql).param("profileId", profileId);
        if (podId != null) {
            spec = spec.param("podId", podId);
        }
        return spec.query(Timestamp.class)
                .list()
                .stream()
                .filter(timestamp -> timestamp != null)
                .map(AppTime::toAppDate)
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

    private boolean columnExists(String table, String column) {
        try (Connection connection = dataSource.getConnection();
             ResultSet columns = connection.getMetaData().getColumns(null, null, table, column)) {
            if (columns.next()) {
                return true;
            }
        } catch (SQLException ignored) {
        }

        try (Connection connection = dataSource.getConnection();
             ResultSet columns = connection.getMetaData().getColumns(null, null, table.toUpperCase(), column.toUpperCase())) {
            return columns.next();
        } catch (SQLException e) {
            throw new IllegalStateException("스트릭 스키마 확인에 실패했습니다", e);
        }
    }

    private boolean tableExists(String table) {
        try (Connection connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData().getTables(null, null, table, null)) {
            if (tables.next()) {
                return true;
            }
        } catch (SQLException ignored) {
        }

        try (Connection connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData().getTables(null, null, table.toUpperCase(), null)) {
            return tables.next();
        } catch (SQLException e) {
            throw new IllegalStateException("스트릭 테이블 확인에 실패했습니다", e);
        }
    }
}
