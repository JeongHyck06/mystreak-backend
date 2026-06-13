package mystreak.backend.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final URI EXPO_PUSH_URL = URI.create("https://exp.host/--/api/v2/push/send");
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JdbcClient jdbcClient;
    private final DataSource dataSource;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        jdbcClient.sql("""
                        CREATE TABLE IF NOT EXISTS push_tokens (
                            token VARCHAR(255) PRIMARY KEY,
                            profile_id VARCHAR(64) NOT NULL,
                            platform VARCHAR(20) NOT NULL,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                        """)
                .update();
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

    @Transactional
    public void registerPushToken(String profileId, String token, String platform) {
        int updated = jdbcClient.sql("""
                        UPDATE push_tokens
                        SET profile_id = :profileId,
                            platform = :platform,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE token = :token
                        """)
                .param("token", token)
                .param("profileId", profileId)
                .param("platform", platform)
                .update();
        if (updated > 0) {
            return;
        }

        try {
            jdbcClient.sql("""
                            INSERT INTO push_tokens (token, profile_id, platform, updated_at)
                            VALUES (:token, :profileId, :platform, CURRENT_TIMESTAMP)
                            """)
                    .param("token", token)
                    .param("profileId", profileId)
                    .param("platform", platform)
                    .update();
        } catch (DuplicateKeyException ignored) {
            jdbcClient.sql("""
                            UPDATE push_tokens
                            SET profile_id = :profileId,
                                platform = :platform,
                                updated_at = CURRENT_TIMESTAMP
                            WHERE token = :token
                            """)
                    .param("token", token)
                    .param("profileId", profileId)
                    .param("platform", platform)
                    .update();
        }
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
        sendPush(recipientId, title, body, type);
    }

    private void sendPush(String recipientId, String title, String body, String type) {
        if (recipientId == null || recipientId.isBlank()) {
            return;
        }

        List<String> tokens = jdbcClient.sql("""
                        SELECT token
                        FROM push_tokens
                        WHERE profile_id = :recipientId
                        """)
                .param("recipientId", recipientId)
                .query(String.class)
                .list();
        if (tokens.isEmpty()) {
            return;
        }

        List<Map<String, Object>> messages = tokens.stream()
                .map(token -> Map.<String, Object>of(
                        "to", token,
                        "title", title,
                        "body", body,
                        "sound", "default",
                        "data", Map.of("type", type)
                ))
                .toList();

        try {
            HttpRequest request = HttpRequest.newBuilder(EXPO_PUSH_URL)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(messages)))
                    .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> logPushResponse(response, tokens.size()))
                    .exceptionally(error -> {
                        log.warn("Expo 푸시 전송 요청에 실패했습니다. tokenCount={}", tokens.size(), error);
                        return null;
                    });
        } catch (JsonProcessingException ignored) {
            // 푸시 전송 실패는 앱 내부 알림 저장을 막지 않는다.
        }
    }

    private void logPushResponse(HttpResponse<String> response, int tokenCount) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("Expo 푸시 전송이 HTTP {}로 실패했습니다. tokenCount={}, body={}",
                    response.statusCode(), tokenCount, response.body());
            return;
        }

        try {
            JsonNode data = objectMapper.readTree(response.body()).path("data");
            if (!data.isArray()) {
                return;
            }
            for (JsonNode ticket : data) {
                if ("error".equals(ticket.path("status").asText())) {
                    log.warn("Expo 푸시 티켓이 거절되었습니다. error={}, message={}, details={}",
                            ticket.path("details").path("error").asText(),
                            ticket.path("message").asText(),
                            ticket.path("details"));
                }
            }
        } catch (JsonProcessingException e) {
            log.warn("Expo 푸시 응답을 해석하지 못했습니다. body={}", response.body(), e);
        }
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
