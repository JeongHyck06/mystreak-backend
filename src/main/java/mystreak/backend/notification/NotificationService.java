package mystreak.backend.notification;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final JdbcClient jdbcClient;

    public NotificationService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<NotificationResponse> getNotifications(String type) {
        if (type == null || type.isBlank() || type.equals("all")) {
            return findNotifications(null);
        }

        return findNotifications(type);
    }

    @Transactional
    public List<NotificationResponse> markAllRead() {
        jdbcClient.sql("UPDATE notifications SET is_read = TRUE")
                .update();
        return getNotifications(null);
    }

    private List<NotificationResponse> findNotifications(String type) {
        String whereClause = type == null ? "" : "WHERE notification_type = :type";
        JdbcClient.StatementSpec statement = jdbcClient.sql("""
                        SELECT id, title, body, meta, notification_type, urgent, is_read
                        FROM notifications
                        %s
                        ORDER BY id
                        """.formatted(whereClause));
        if (type != null) {
            statement = statement.param("type", type);
        }
        return statement.query((rs, rowNum) -> new NotificationResponse(
                        rs.getString("id"),
                        rs.getString("title"),
                        rs.getString("body"),
                        rs.getString("meta"),
                        rs.getString("notification_type"),
                        rs.getBoolean("urgent"),
                        rs.getBoolean("is_read")
                ))
                .list();
    }
}
