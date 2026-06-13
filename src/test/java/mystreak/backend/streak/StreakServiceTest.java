package mystreak.backend.streak;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.sql.DataSource;
import mystreak.backend.common.AppTime;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class StreakServiceTest {

    @Test
    void weeklyChecksCountDistinctDaysNotPosts() {
        Fixture fixture = fixture();
        JdbcClient jdbcClient = fixture.jdbcClient();
        StreakService service = new StreakService(jdbcClient, fixture.dataSource());

        jdbcClient.sql("INSERT INTO profiles (id, name, handle, email) VALUES ('me', '나', '@me', 'me@example.com')").update();
        jdbcClient.sql("INSERT INTO pods (id, name, description, max_members, tag_line) VALUES ('pod', '팟', '설명', 8, '사진')").update();
        jdbcClient.sql("INSERT INTO pod_members (pod_id, profile_id) VALUES ('pod', 'me')").update();
        Instant todayNoon = AppTime.startOfToday().toInstant().plus(12, ChronoUnit.HOURS);
        insertVerifiedCheckIn(jdbcClient, "feed-1", Timestamp.from(todayNoon));
        insertVerifiedCheckIn(jdbcClient, "feed-2", Timestamp.from(todayNoon.plus(1, ChronoUnit.HOURS)));

        service.recalculateProfile("me");

        Integer weeklyChecks = jdbcClient.sql("SELECT weekly_checks FROM user_stats WHERE profile_id = 'me'")
                .query(Integer.class)
                .single();
        Integer totalChecks = jdbcClient.sql("SELECT total_checks FROM user_stats WHERE profile_id = 'me'")
                .query(Integer.class)
                .single();

        assertThat(weeklyChecks).isEqualTo(1);
        assertThat(totalChecks).isEqualTo(2);
    }

    @Test
    void trophiesReflectCurrentStreakMilestones() {
        Fixture fixture = fixture();
        JdbcClient jdbcClient = fixture.jdbcClient();
        StreakService service = new StreakService(jdbcClient, fixture.dataSource());

        jdbcClient.sql("INSERT INTO profiles (id, name, handle, email) VALUES ('me', '나', '@me', 'me@example.com')").update();
        jdbcClient.sql("INSERT INTO pods (id, name, description, max_members, tag_line) VALUES ('pod', '팟', '설명', 8, '사진')").update();
        jdbcClient.sql("INSERT INTO pod_members (pod_id, profile_id) VALUES ('pod', 'me')").update();
        for (int daysAgo = 0; daysAgo < 30; daysAgo++) {
            insertVerifiedCheckIn(jdbcClient, "feed-" + daysAgo, Timestamp.from(Instant.now().minus(daysAgo, ChronoUnit.DAYS)));
        }

        service.recalculateProfile("me");

        Integer trophies = jdbcClient.sql("SELECT trophies FROM profiles WHERE id = 'me'")
                .query(Integer.class)
                .single();
        String recentTrophy = jdbcClient.sql("SELECT recent_trophy FROM user_stats WHERE profile_id = 'me'")
                .query(String.class)
                .single();

        assertThat(trophies).isEqualTo(1);
        assertThat(recentTrophy).isEqualTo("30일 연속 달성!");
    }

    private Fixture fixture() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:streak-test-" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        JdbcClient jdbcClient = JdbcClient.create(dataSource);
        jdbcClient.sql("""
                        CREATE TABLE profiles (
                            id VARCHAR(64) PRIMARY KEY,
                            name VARCHAR(80),
                            handle VARCHAR(80),
                            email VARCHAR(120),
                            current_streak INT DEFAULT 0,
                            best_streak INT DEFAULT 0,
                            total_checks INT DEFAULT 0,
                            trophies INT DEFAULT 0
                        )
                        """).update();
        jdbcClient.sql("""
                        CREATE TABLE pods (
                            id VARCHAR(120) PRIMARY KEY,
                            name VARCHAR(120),
                            description VARCHAR(500),
                            certified_today INT DEFAULT 0,
                            max_members INT DEFAULT 8,
                            streak INT DEFAULT 0,
                            tag_line VARCHAR(120),
                            needs_check_in BOOLEAN DEFAULT TRUE
                        )
                        """).update();
        jdbcClient.sql("""
                        CREATE TABLE pod_members (
                            pod_id VARCHAR(120),
                            profile_id VARCHAR(64),
                            streak INT DEFAULT 0,
                            checked_in_today BOOLEAN DEFAULT FALSE
                        )
                        """).update();
        jdbcClient.sql("""
                        CREATE TABLE check_ins (
                            id VARCHAR(120) PRIMARY KEY,
                            pod_id VARCHAR(120),
                            author_id VARCHAR(64),
                            created_at TIMESTAMP
                        )
                        """).update();
        jdbcClient.sql("""
                        CREATE TABLE check_in_checks (
                            check_in_id VARCHAR(120),
                            profile_id VARCHAR(64)
                        )
                        """).update();
        jdbcClient.sql("""
                        CREATE TABLE user_stats (
                            profile_id VARCHAR(64),
                            stat_year INT,
                            stat_month INT,
                            current_streak INT,
                            best_streak INT,
                            weekly_checks INT,
                            weekly_goal INT,
                            total_checks INT,
                            active_pods INT,
                            monthly_completion_rate INT,
                            checked_days_in_month INT,
                            heatmap VARCHAR(120),
                            recent_trophy VARCHAR(120),
                            PRIMARY KEY (profile_id, stat_year, stat_month)
                        )
                        """).update();
        return new Fixture(dataSource, jdbcClient);
    }

    private void insertVerifiedCheckIn(JdbcClient jdbcClient, String id, Timestamp createdAt) {
        jdbcClient.sql("INSERT INTO check_ins (id, pod_id, author_id, created_at) VALUES (:id, 'pod', 'me', :createdAt)")
                .param("id", id)
                .param("createdAt", createdAt)
                .update();
        jdbcClient.sql("INSERT INTO check_in_checks (check_in_id, profile_id) VALUES (:id, 'other')")
                .param("id", id)
                .update();
    }

    private record Fixture(DataSource dataSource, JdbcClient jdbcClient) {
    }
}
