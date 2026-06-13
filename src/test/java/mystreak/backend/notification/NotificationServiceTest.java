package mystreak.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class NotificationServiceTest {

    @Test
    void notificationsCanBeFilteredAndMarkedRead() {
        Fixture fixture = fixture();

        fixture.service.notifyCheck("me", "지수", "러닝", 2);
        fixture.service.notifyComment("me", "지수", "러닝", "오늘 인증 멋져요");

        List<NotificationResponse> checkNotifications = fixture.service.getNotifications("me", "check");

        assertThat(fixture.service.getNotifications("me", "all")).hasSize(2);
        assertThat(checkNotifications).hasSize(1);
        assertThat(checkNotifications.get(0).type()).isEqualTo("check");
        assertThat(checkNotifications.get(0).read()).isFalse();

        assertThat(fixture.service.markAllRead("me"))
                .allMatch(NotificationResponse::read);
    }

    @Test
    void pushTokenRegistrationUpdatesExistingToken() {
        Fixture fixture = fixture();

        fixture.service.registerPushToken("me", "ExponentPushToken[test]", "ios");
        fixture.service.registerPushToken("other", "ExponentPushToken[test]", "android");

        String owner = fixture.jdbcClient.sql("SELECT profile_id FROM push_tokens WHERE token = 'ExponentPushToken[test]'")
                .query(String.class)
                .single();
        String platform = fixture.jdbcClient.sql("SELECT platform FROM push_tokens WHERE token = 'ExponentPushToken[test]'")
                .query(String.class)
                .single();

        assertThat(owner).isEqualTo("other");
        assertThat(platform).isEqualTo("android");
    }

    private Fixture fixture() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:notification-test-" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        JdbcClient jdbcClient = JdbcClient.create(dataSource);
        NotificationService service = new NotificationService(jdbcClient, dataSource);
        service.ensureNotificationTable();
        return new Fixture(jdbcClient, service);
    }

    private record Fixture(JdbcClient jdbcClient, NotificationService service) {
    }
}
