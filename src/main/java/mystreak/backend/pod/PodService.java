package mystreak.backend.pod;

import jakarta.annotation.PostConstruct;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;
import mystreak.backend.common.AppTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PodService {

    private static final String INVITE_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int MAX_ID_ATTEMPTS = 50;

    private final JdbcClient jdbcClient;
    private final DataSource dataSource;
    private final SecureRandom secureRandom = new SecureRandom();

    public PodService(JdbcClient jdbcClient, DataSource dataSource) {
        this.jdbcClient = jdbcClient;
        this.dataSource = dataSource;
    }

    @PostConstruct
    void ensurePodColumns() {
        if (tableExists("pods") && !columnExists("pods", "avatar_url")) {
            jdbcClient.sql("ALTER TABLE pods ADD COLUMN avatar_url VARCHAR(500)").update();
        }
        if (tableExists("pod_members")) {
            jdbcClient.sql("UPDATE pod_members SET member_role = '방장' WHERE member_role = '나'").update();
        }
    }

    public List<PodResponse> getMyPods(String profileId) {
        Timestamp startOfDay = AppTime.startOfToday();
        Timestamp startOfNextDay = AppTime.startOfTomorrow();
        return jdbcClient.sql("""
                        SELECT p.id, p.name, p.description, p.avatar_url,
                               (SELECT COUNT(*) FROM pod_members pmc WHERE pmc.pod_id = p.id) AS member_count,
                               p.certified_today, p.max_members,
                               p.streak, p.tag_line, p.invite_code,
                               (SELECT COUNT(*) FROM check_ins ci
                                WHERE ci.pod_id = p.id AND ci.author_id = :profileId
                                  AND ci.created_at >= :startOfDay
                                  AND ci.created_at < :startOfNextDay) AS my_checks_today
                        FROM pods p
                        JOIN pod_members pm ON pm.pod_id = p.id
                        WHERE pm.profile_id = :profileId
                        ORDER BY p.id
                        """)
                .param("profileId", profileId)
                .param("startOfDay", startOfDay)
                .param("startOfNextDay", startOfNextDay)
                .query((rs, rowNum) -> toPodResponse(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getInt("member_count"),
                        rs.getInt("certified_today"),
                        rs.getInt("max_members"),
                        rs.getInt("streak"),
                        rs.getString("tag_line"),
                        rs.getInt("my_checks_today") == 0,
                        rs.getString("invite_code"),
                        rs.getString("avatar_url")
                ))
                .list();
    }

    public PodResponse getPod(String podId) {
        return jdbcClient.sql("""
                        SELECT id, name, description, avatar_url,
                               (SELECT COUNT(*) FROM pod_members pmc WHERE pmc.pod_id = pods.id) AS member_count,
                               certified_today, max_members,
                               streak, tag_line, needs_check_in, invite_code
                        FROM pods
                        WHERE id = :id
                        """)
                .param("id", podId)
                .query((rs, rowNum) -> toPodResponse(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getInt("member_count"),
                        rs.getInt("certified_today"),
                        rs.getInt("max_members"),
                        rs.getInt("streak"),
                        rs.getString("tag_line"),
                        rs.getBoolean("needs_check_in"),
                        rs.getString("invite_code"),
                        rs.getString("avatar_url")
                ))
                .optional()
                .orElseThrow(() -> new PodNotFoundException(podId));
    }

    @Transactional
    public PodResponse createPod(String profileId, CreatePodRequest request) {
        String slug = toSlug(request.name());
        String id = insertPod(slug, request);

        insertTags(id, request.tags());
        jdbcClient.sql("""
                        INSERT INTO pod_members (pod_id, profile_id, member_role, streak, checked_in_today)
                        VALUES (:podId, :profileId, '방장', 0, FALSE)
                        """)
                .param("podId", id)
                .param("profileId", profileId)
                .update();
        return getPod(id);
    }

    private String toSlug(String name) {
        String slug = name
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9가-힣]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "pod" : slug;
    }

    /**
     * id(PK)와 invite_code(UNIQUE) 충돌을 사전 조회 없이 처리한다.
     * 동시 요청으로 같은 slug가 들어와도 INSERT 실패 시 고유 suffix를 붙여 재시도하므로
     * check-then-insert 경쟁 조건이 발생하지 않는다.
     */
    private String insertPod(String slug, CreatePodRequest request) {
        for (int attempt = 0; attempt < MAX_ID_ATTEMPTS; attempt++) {
            String id = attempt == 0 ? slug : slug + "-" + randomSuffix(6);
            try {
                jdbcClient.sql("""
                                INSERT INTO pods (id, name, description, avatar_url, member_count, certified_today, max_members, streak, tag_line, needs_check_in, invite_code)
                                VALUES (:id, :name, :description, :avatarUrl, 1, 0, :maxMembers, 0, :tagLine, TRUE, :inviteCode)
                                """)
                        .param("id", id)
                        .param("name", request.name())
                        .param("description", request.description())
                        .param("avatarUrl", blankToNull(request.avatarUrl()))
                        .param("maxMembers", request.maxMembers())
                        .param("tagLine", request.tagLine())
                        .param("inviteCode", generateInviteCode(id))
                        .update();
                return id;
            } catch (DuplicateKeyException ignored) {
                // id 또는 invite_code 충돌: 새 suffix로 재시도
            }
        }
        throw new IllegalStateException("팟 ID 생성에 실패했습니다: " + slug);
    }

    public PodResponse previewJoin(String inviteCode) {
        return jdbcClient.sql("""
                        SELECT id, name, description, avatar_url,
                               (SELECT COUNT(*) FROM pod_members pmc WHERE pmc.pod_id = pods.id) AS member_count,
                               certified_today, max_members,
                               streak, tag_line, needs_check_in, invite_code
                        FROM pods
                        WHERE UPPER(invite_code) = UPPER(:inviteCode)
                        """)
                .param("inviteCode", inviteCode)
                .query((rs, rowNum) -> toPodResponse(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getInt("member_count"),
                        rs.getInt("certified_today"),
                        rs.getInt("max_members"),
                        rs.getInt("streak"),
                        rs.getString("tag_line"),
                        rs.getBoolean("needs_check_in"),
                        rs.getString("invite_code"),
                        rs.getString("avatar_url")
                ))
                .optional()
                .orElseThrow(() -> new PodNotFoundException(inviteCode));
    }

    @Transactional
    public PodResponse joinPod(String profileId, JoinPodRequest request) {
        PodResponse pod = previewJoin(request.inviteCode());
        jdbcClient.sql("""
                        INSERT INTO pod_members (pod_id, profile_id, member_role, streak, checked_in_today)
                        VALUES (:podId, :profileId, '멤버', 0, FALSE)
                        ON DUPLICATE KEY UPDATE member_role = member_role
                        """)
                .param("podId", pod.id())
                .param("profileId", profileId)
                .update();
        jdbcClient.sql("""
                        UPDATE pods
                        SET member_count = LEAST(member_count + 1, max_members)
                        WHERE id = :id
                        """)
                .param("id", pod.id())
                .update();
        return getPod(pod.id());
    }

    @Transactional
    public void leavePod(String profileId, String podId) {
        getPod(podId);
        jdbcClient.sql("""
                        DELETE FROM pod_members
                        WHERE pod_id = :podId AND profile_id = :profileId
                        """)
                .param("podId", podId)
                .param("profileId", profileId)
                .update();

        int remainingMembers = jdbcClient.sql("""
                        SELECT COUNT(*) FROM pod_members
                        WHERE pod_id = :podId
                        """)
                .param("podId", podId)
                .query(Integer.class)
                .single();

        if (remainingMembers == 0) {
            // 마지막 멤버가 나가면 팟을 삭제한다. pod_tags, pod_members, check_ins 등은
            // ON DELETE CASCADE로 함께 정리된다.
            jdbcClient.sql("DELETE FROM pods WHERE id = :podId")
                    .param("podId", podId)
                    .update();
            return;
        }

        jdbcClient.sql("""
                        UPDATE pods
                        SET member_count = GREATEST(member_count - 1, 0)
                        WHERE id = :podId
                        """)
                .param("podId", podId)
                .update();
    }

    public List<PodMemberResponse> getMembers(String profileId, String podId) {
        getPod(podId);
        return jdbcClient.sql("""
                        SELECT p.id, p.name, p.handle, p.avatar_url, pm.streak, pm.checked_in_today, pm.member_role
                        FROM pod_members pm
                        JOIN profiles p ON p.id = pm.profile_id
                        WHERE pm.pod_id = :podId
                        ORDER BY pm.member_role DESC, p.name
                        """)
                .param("podId", podId)
                .query((rs, rowNum) -> new PodMemberResponse(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("handle"),
                        rs.getInt("streak"),
                        rs.getBoolean("checked_in_today"),
                        profileId.equals(rs.getString("id")) ? "나" : rs.getString("member_role"),
                        rs.getString("avatar_url")
                ))
                .list();
    }

    public InviteResponse inviteMember(String podId, InviteMemberRequest request) {
        PodResponse pod = getPod(podId);
        return new InviteResponse(
                pod.id(),
                request.handle(),
                "sent",
                "mystreak.app/pod/" + pod.inviteCode()
        );
    }

    private PodResponse toPodResponse(
            String id,
            String name,
            String description,
            int memberCount,
            int certifiedToday,
            int maxMembers,
            int streak,
            String tagLine,
            boolean needsCheckIn,
            String inviteCode,
            String avatarUrl
    ) {
        return new PodResponse(
                id,
                name,
                description,
                memberCount,
                certifiedToday,
                maxMembers,
                streak,
                tagLine,
                getTags(id),
                needsCheckIn,
                inviteCode,
                avatarUrl
        );
    }

    private List<String> getTags(String podId) {
        return jdbcClient.sql("""
                        SELECT tag
                        FROM pod_tags
                        WHERE pod_id = :podId
                        ORDER BY sort_order, tag
                        """)
                .param("podId", podId)
                .query(String.class)
                .list();
    }

    private void insertTags(String podId, List<String> tags) {
        for (int index = 0; index < tags.size(); index++) {
            jdbcClient.sql("""
                            INSERT INTO pod_tags (pod_id, tag, sort_order)
                            VALUES (:podId, :tag, :sortOrder)
                            """)
                    .param("podId", podId)
                    .param("tag", tags.get(index))
                    .param("sortOrder", index + 1)
                    .update();
        }
    }

    private String generateInviteCode(String podId) {
        String base = podId.replaceAll("[^a-zA-Z0-9]", "").toUpperCase(Locale.ROOT);
        if (base.isBlank()) {
            base = "POD";
        }
        if (base.length() > 8) {
            base = base.substring(0, 8);
        }
        for (int attempt = 0; attempt < 50; attempt++) {
            String code = base + randomSuffix(4);
            if (!inviteCodeExists(code)) {
                return code;
            }
        }
        return base + System.currentTimeMillis();
    }

    private String randomSuffix(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(INVITE_CODE_ALPHABET.charAt(secureRandom.nextInt(INVITE_CODE_ALPHABET.length())));
        }
        return builder.toString();
    }

    private boolean inviteCodeExists(String inviteCode) {
        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM pods WHERE UPPER(invite_code) = UPPER(:inviteCode)")
                .param("inviteCode", inviteCode)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    private boolean tableExists(String tableName) {
        try (Connection connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData().getTables(null, null, tableName, null)) {
            if (tables.next()) {
                return true;
            }
        } catch (SQLException ignored) {
        }

        try (Connection connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData().getTables(null, null, tableName.toUpperCase(Locale.ROOT), null)) {
            return tables.next();
        } catch (SQLException e) {
            throw new IllegalStateException("팟 테이블 확인에 실패했습니다", e);
        }
    }

    private boolean columnExists(String tableName, String columnName) {
        try (Connection connection = dataSource.getConnection();
             ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
            if (columns.next()) {
                return true;
            }
        } catch (SQLException ignored) {
        }

        try (Connection connection = dataSource.getConnection();
             ResultSet columns = connection.getMetaData().getColumns(null, null, tableName.toUpperCase(Locale.ROOT), columnName.toUpperCase(Locale.ROOT))) {
            return columns.next();
        } catch (SQLException e) {
            throw new IllegalStateException("팟 스키마 확인에 실패했습니다", e);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
