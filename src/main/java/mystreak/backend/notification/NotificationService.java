package mystreak.backend.notification;

import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final JdbcClient jdbcClient;
    private final DataSource dataSource;

    public NotificationService(JdbcClient jdbcClient, DataSource dataSource) {
        this.jdbcClient = jdbcClient;
        this.dataSource = dataSource;
    }

    @PostConstruct
    void ensureNotificationTable() {
        jdbcClient.sql("""
                        CREATE TABLE IF NOT EXISTS notifications (
                            id VARCHAR(120) PRIMARY KEY,
                            recipient_id VARCHAR(64) NULL,
                            title VARCHAR(160) NOT NULL,
                            body VARCHAR(300) NOT NULL,
                            meta VARCHAR(80) NOT NULL,
                            notification_type VARCHAR(40) NOT NULL,
                            urgent BOOLEAN NOT NULL DEFAULT FALSE,
                            is_read BOOLEAN NOT NULL DEFAULT FALSE,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                        """)
                .update();
        if (!columnExists("notifications", "recipient_id")) {
            jdbcClient.sql("ALTER TABLE notifications ADD COLUMN recipient_id VARCHAR(64) NULL").update();
        }
        if (!columnExists("notifications", "created_at")) {
            jdbcClient.sql("ALTER TABLE notifications ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP").update();
        }
    }

    public List<NotificationResponse> getNotifications(String profileId, String type) {
        if (type == null || type.isBlank() || type.equals("all")) {
            return findNotifications(profileId, null);
        }

        return findNotifications(profileId, type);
    }

    @Transactional
    public List<NotificationResponse> markAllRead(String profileId) {
        jdbcClient.sql("UPDATE notifications SET is_read = TRUE WHERE recipient_id = :profileId OR recipient_id IS NULL")
                .param("profileId", profileId)
                .update();
        return getNotifications(profileId, null);
    }

    @Transactional
    public void notifyCheck(String recipientId, String actorName, String podName, int checks) {
        create(recipientId, "%s님이 내 인증을 체크했어요".formatted(actorName),
                "%s · %d명이 확인했어요".formatted(podName, checks),
                "방금 전", "check", false);
    }

    @Transactional
    public void notifyLike(String recipientId, String actorName, String podName) {
        create(recipientId, "%s님이 내 인증에 좋아요를 눌렀어요".formatted(actorName),
                podName, "방금 전", "like", false);
    }

    @Transactional
    public void notifyComment(String recipientId, String actorName, String podName, String commentText) {
        String preview = commentText.length() > 40 ? commentText.substring(0, 40) + "..." : commentText;
        create(recipientId, "%s님이 내 인증에 댓글을 남겼어요".formatted(actorName),
                "%s · %s".formatted(podName, preview), "방금 전", "comment", true);
    }

    private void create(String recipientId, String title, String body, String meta, String type, boolean urgent) {
        jdbcClient.sql("""
                        INSERT INTO notifications (id, recipient_id, title, body, meta, notification_type, urgent, is_read)
                        VALUES (:id, :recipientId, :title, :body, :meta, :type, :urgent, FALSE)
                        """)
                .param("id", "noti-" + UUID.randomUUID())
                .param("recipientId", recipientId)
                .param("title", title)
                .param("body", body)
                .param("meta", meta)
                .param("type", type)
                .param("urgent", urgent)
                .update();
    }

    private List<NotificationResponse> findNotifications(String profileId, String type) {
        String typeClause = type == null ? "" : "AND notification_type = :type";
        JdbcClient.StatementSpec statement = jdbcClient.sql("""
                        SELECT id, title, body, meta, notification_type, urgent, is_read
                        FROM notifications
                        WHERE (recipient_id = :profileId OR recipient_id IS NULL)
                        %s
                        ORDER BY created_at DESC, id DESC
                        """.formatted(typeClause))
                .param("profileId", profileId);
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
            throw new IllegalStateException("알림 스키마 확인에 실패했습니다", e);
        }
    }
}
