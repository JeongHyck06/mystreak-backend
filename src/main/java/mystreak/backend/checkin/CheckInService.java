package mystreak.backend.checkin;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckInService {

    private final JdbcClient jdbcClient;

    public CheckInService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<CheckInResponse> getPodFeed(String podId) {
        return jdbcClient.sql("""
                        SELECT ci.id, ci.pod_id, p.name AS author, ci.meta, ci.text, ci.media_url,
                               ci.likes, ci.comments, ci.checked_by_me
                        FROM check_ins ci
                        JOIN profiles p ON p.id = ci.author_id
                        WHERE ci.pod_id = :podId
                        ORDER BY ci.created_at DESC, ci.id DESC
                        """)
                .param("podId", podId)
                .query((rs, rowNum) -> new CheckInResponse(
                        rs.getString("id"),
                        rs.getString("pod_id"),
                        rs.getString("author"),
                        rs.getString("meta"),
                        rs.getString("text"),
                        rs.getString("media_url"),
                        rs.getInt("likes"),
                        rs.getInt("comments"),
                        rs.getBoolean("checked_by_me")
                ))
                .list();
    }

    @Transactional
    public CheckInResponse createCheckIn(String podId, CreateCheckInRequest request) {
        String id = "feed-" + (countCheckIns() + 1);
        jdbcClient.sql("""
                        INSERT INTO check_ins (id, pod_id, author_id, meta, text, media_url, likes, comments, checked_by_me)
                        VALUES (:id, :podId, 'me', '방금 전', :text, :mediaUrl, 0, 0, FALSE)
                        """)
                .param("id", id)
                .param("podId", podId)
                .param("text", request.text())
                .param("mediaUrl", request.mediaUrl())
                .update();
        return getCheckIn(id);
    }

    @Transactional
    public CheckReactionResponse check(String checkInId) {
        CheckInResponse current = getCheckIn(checkInId);
        if (current.checkedByMe()) {
            return new CheckReactionResponse(current.id(), true, current.likes());
        }

        jdbcClient.sql("""
                        UPDATE check_ins
                        SET likes = likes + 1, checked_by_me = TRUE
                        WHERE id = :id
                        """)
                .param("id", checkInId)
                .update();

        CheckInResponse checked = getCheckIn(checkInId);
        return new CheckReactionResponse(checked.id(), checked.checkedByMe(), checked.likes());
    }

    public CheckInResponse getCheckIn(String checkInId) {
        return jdbcClient.sql("""
                        SELECT ci.id, ci.pod_id, p.name AS author, ci.meta, ci.text, ci.media_url,
                               ci.likes, ci.comments, ci.checked_by_me
                        FROM check_ins ci
                        JOIN profiles p ON p.id = ci.author_id
                        WHERE ci.id = :id
                        """)
                .param("id", checkInId)
                .query((rs, rowNum) -> new CheckInResponse(
                        rs.getString("id"),
                        rs.getString("pod_id"),
                        rs.getString("author"),
                        rs.getString("meta"),
                        rs.getString("text"),
                        rs.getString("media_url"),
                        rs.getInt("likes"),
                        rs.getInt("comments"),
                        rs.getBoolean("checked_by_me")
                ))
                .optional()
                .orElseThrow(() -> new CheckInNotFoundException(checkInId));
    }

    public List<CheckInResponse> allCheckIns() {
        return jdbcClient.sql("""
                        SELECT ci.id, ci.pod_id, p.name AS author, ci.meta, ci.text, ci.media_url,
                               ci.likes, ci.comments, ci.checked_by_me
                        FROM check_ins ci
                        JOIN profiles p ON p.id = ci.author_id
                        ORDER BY ci.created_at DESC, ci.id DESC
                        """)
                .query((rs, rowNum) -> new CheckInResponse(
                        rs.getString("id"),
                        rs.getString("pod_id"),
                        rs.getString("author"),
                        rs.getString("meta"),
                        rs.getString("text"),
                        rs.getString("media_url"),
                        rs.getInt("likes"),
                        rs.getInt("comments"),
                        rs.getBoolean("checked_by_me")
                ))
                .list();
    }

    private int countCheckIns() {
        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM check_ins")
                .query(Integer.class)
                .single();
        return count == null ? 0 : count;
    }
}
