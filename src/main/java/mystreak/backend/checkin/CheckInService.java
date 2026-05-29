package mystreak.backend.checkin;

import jakarta.annotation.PostConstruct;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import mystreak.backend.streak.StreakService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CheckInService {

    private static final String FEED_SELECT = """
            SELECT ci.id, ci.pod_id, ci.author_id, p.name AS author, ci.meta, ci.text, ci.media_url,
                   (SELECT COUNT(*) FROM check_in_likes l WHERE l.check_in_id = ci.id) AS like_count,
                   (SELECT COUNT(*) FROM check_in_likes l WHERE l.check_in_id = ci.id AND l.profile_id = :me) AS liked_by_me,
                   (SELECT COUNT(*) FROM check_in_checks c WHERE c.check_in_id = ci.id) AS check_count,
                   (SELECT COUNT(*) FROM check_in_checks c WHERE c.check_in_id = ci.id AND c.profile_id = :me) AS checked_by_me,
                   (SELECT COUNT(*) FROM check_in_comments cm WHERE cm.check_in_id = ci.id) AS comment_count
            FROM check_ins ci
            JOIN profiles p ON p.id = ci.author_id
            """;

    private final JdbcClient jdbcClient;
    private final StreakService streakService;

    public CheckInService(JdbcClient jdbcClient, StreakService streakService) {
        this.jdbcClient = jdbcClient;
        this.streakService = streakService;
    }

    @PostConstruct
    void ensureReactionTables() {
        jdbcClient.sql("""
                        CREATE TABLE IF NOT EXISTS check_in_likes (
                            check_in_id VARCHAR(120) NOT NULL,
                            profile_id VARCHAR(64) NOT NULL,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            PRIMARY KEY (check_in_id, profile_id)
                        )
                        """)
                .update();
        jdbcClient.sql("""
                        CREATE TABLE IF NOT EXISTS check_in_checks (
                            check_in_id VARCHAR(120) NOT NULL,
                            profile_id VARCHAR(64) NOT NULL,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            PRIMARY KEY (check_in_id, profile_id)
                        )
                        """)
                .update();
        jdbcClient.sql("""
                        CREATE TABLE IF NOT EXISTS check_in_comments (
                            id VARCHAR(120) PRIMARY KEY,
                            check_in_id VARCHAR(120) NOT NULL,
                            author_id VARCHAR(64) NOT NULL,
                            text VARCHAR(300) NOT NULL,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                        """)
                .update();
    }

    public List<CheckInResponse> getPodFeed(String profileId, String podId) {
        return jdbcClient.sql(FEED_SELECT + """
                        WHERE ci.pod_id = :podId
                        ORDER BY ci.created_at DESC, ci.id DESC
                        """)
                .param("me", profileId)
                .param("podId", podId)
                .query((rs, rowNum) -> mapCheckIn(rs, profileId))
                .list();
    }

    @Transactional
    public CheckInResponse createCheckIn(String profileId, String podId, CreateCheckInRequest request) {
        if (hasCheckedInToday(profileId, podId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "오늘 이미 인증을 완료했어요. 인증 글은 수정하거나 삭제할 수 있어요.");
        }

        String id = "feed-" + UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO check_ins (id, pod_id, author_id, meta, text, media_url, likes, comments, checked_by_me)
                        VALUES (:id, :podId, :authorId, '방금 전', :text, :mediaUrl, 0, 0, FALSE)
                        """)
                .param("id", id)
                .param("podId", podId)
                .param("authorId", profileId)
                .param("text", request.text())
                .param("mediaUrl", request.mediaUrl())
                .update();

        streakService.recalculateProfile(profileId);
        streakService.recalculatePod(podId);
        return getCheckIn(id, profileId);
    }

    @Transactional
    public CheckInResponse updateCheckIn(String profileId, String checkInId, UpdateCheckInRequest request) {
        requireOwnership(profileId, checkInId);
        jdbcClient.sql("UPDATE check_ins SET text = :text WHERE id = :id")
                .param("text", request.text())
                .param("id", checkInId)
                .update();
        return getCheckIn(checkInId, profileId);
    }

    @Transactional
    public void deleteCheckIn(String profileId, String checkInId) {
        requireOwnership(profileId, checkInId);
        String podId = podIdOf(checkInId);
        jdbcClient.sql("DELETE FROM check_in_likes WHERE check_in_id = :id").param("id", checkInId).update();
        jdbcClient.sql("DELETE FROM check_in_checks WHERE check_in_id = :id").param("id", checkInId).update();
        jdbcClient.sql("DELETE FROM check_in_comments WHERE check_in_id = :id").param("id", checkInId).update();
        jdbcClient.sql("DELETE FROM check_ins WHERE id = :id").param("id", checkInId).update();

        streakService.recalculateProfile(profileId);
        if (podId != null) {
            streakService.recalculatePod(podId);
        }
    }

    @Transactional
    public CheckInResponse toggleLike(String profileId, String checkInId) {
        getCheckIn(checkInId, profileId);
        int removed = jdbcClient.sql("DELETE FROM check_in_likes WHERE check_in_id = :id AND profile_id = :me")
                .param("id", checkInId)
                .param("me", profileId)
                .update();
        if (removed == 0) {
            jdbcClient.sql("INSERT INTO check_in_likes (check_in_id, profile_id) VALUES (:id, :me)")
                    .param("id", checkInId)
                    .param("me", profileId)
                    .update();
        }
        return getCheckIn(checkInId, profileId);
    }

    @Transactional
    public CheckInResponse toggleCheck(String profileId, String checkInId) {
        CheckInResponse checkIn = getCheckIn(checkInId, profileId);
        if (checkIn.authorId().equals(profileId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "본인 인증은 체크할 수 없어요.");
        }
        int removed = jdbcClient.sql("DELETE FROM check_in_checks WHERE check_in_id = :id AND profile_id = :me")
                .param("id", checkInId)
                .param("me", profileId)
                .update();
        if (removed == 0) {
            jdbcClient.sql("INSERT INTO check_in_checks (check_in_id, profile_id) VALUES (:id, :me)")
                    .param("id", checkInId)
                    .param("me", profileId)
                    .update();
        }
        return getCheckIn(checkInId, profileId);
    }

    public List<CommentResponse> getComments(String profileId, String checkInId) {
        getCheckIn(checkInId, profileId);
        return jdbcClient.sql("""
                        SELECT cm.id, cm.check_in_id, cm.author_id, p.name AS author, cm.text
                        FROM check_in_comments cm
                        JOIN profiles p ON p.id = cm.author_id
                        WHERE cm.check_in_id = :checkInId
                        ORDER BY cm.created_at ASC, cm.id ASC
                        """)
                .param("checkInId", checkInId)
                .query((rs, rowNum) -> new CommentResponse(
                        rs.getString("id"),
                        rs.getString("check_in_id"),
                        rs.getString("author_id"),
                        rs.getString("author"),
                        rs.getString("text"),
                        "방금 전",
                        rs.getString("author_id").equals(profileId)
                ))
                .list();
    }

    @Transactional
    public CommentResponse addComment(String profileId, String checkInId, CreateCommentRequest request) {
        getCheckIn(checkInId, profileId);
        String id = "comment-" + UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO check_in_comments (id, check_in_id, author_id, text)
                        VALUES (:id, :checkInId, :authorId, :text)
                        """)
                .param("id", id)
                .param("checkInId", checkInId)
                .param("authorId", profileId)
                .param("text", request.text())
                .update();

        String author = jdbcClient.sql("SELECT name FROM profiles WHERE id = :id")
                .param("id", profileId)
                .query(String.class)
                .optional()
                .orElse("");
        return new CommentResponse(id, checkInId, profileId, author, request.text(), "방금 전", true);
    }

    public CheckInResponse getCheckIn(String checkInId, String profileId) {
        return jdbcClient.sql(FEED_SELECT + "WHERE ci.id = :id")
                .param("me", profileId)
                .param("id", checkInId)
                .query((rs, rowNum) -> mapCheckIn(rs, profileId))
                .optional()
                .orElseThrow(() -> new CheckInNotFoundException(checkInId));
    }

    private boolean hasCheckedInToday(String profileId, String podId) {
        Timestamp startOfDay = Timestamp.valueOf(LocalDate.now().atStartOfDay());
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM check_ins
                        WHERE author_id = :profileId AND pod_id = :podId AND created_at >= :startOfDay
                        """)
                .param("profileId", profileId)
                .param("podId", podId)
                .param("startOfDay", startOfDay)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    private void requireOwnership(String profileId, String checkInId) {
        String authorId = jdbcClient.sql("SELECT author_id FROM check_ins WHERE id = :id")
                .param("id", checkInId)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new CheckInNotFoundException(checkInId));
        if (!authorId.equals(profileId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인이 올린 인증만 수정하거나 삭제할 수 있어요.");
        }
    }

    private String podIdOf(String checkInId) {
        return jdbcClient.sql("SELECT pod_id FROM check_ins WHERE id = :id")
                .param("id", checkInId)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private CheckInResponse mapCheckIn(java.sql.ResultSet rs, String profileId) throws java.sql.SQLException {
        String authorId = rs.getString("author_id");
        return new CheckInResponse(
                rs.getString("id"),
                rs.getString("pod_id"),
                authorId,
                rs.getString("author"),
                rs.getString("meta"),
                rs.getString("text"),
                rs.getString("media_url"),
                rs.getInt("like_count"),
                rs.getInt("liked_by_me") > 0,
                rs.getInt("check_count"),
                rs.getInt("checked_by_me") > 0,
                rs.getInt("comment_count"),
                authorId != null && authorId.equals(profileId)
        );
    }
}
