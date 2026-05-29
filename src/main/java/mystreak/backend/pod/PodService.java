package mystreak.backend.pod;

import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PodService {

    private final JdbcClient jdbcClient;

    public PodService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<PodResponse> getMyPods(String profileId) {
        return jdbcClient.sql("""
                        SELECT p.id, p.name, p.description, p.member_count, p.certified_today, p.max_members,
                               p.streak, p.tag_line, p.needs_check_in, p.invite_code
                        FROM pods p
                        JOIN pod_members pm ON pm.pod_id = p.id
                        WHERE pm.profile_id = :profileId
                        ORDER BY p.id
                        """)
                .param("profileId", profileId)
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
                        rs.getString("invite_code")
                ))
                .list();
    }

    public PodResponse getPod(String podId) {
        return jdbcClient.sql("""
                        SELECT id, name, description, member_count, certified_today, max_members,
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
                        rs.getString("invite_code")
                ))
                .optional()
                .orElseThrow(() -> new PodNotFoundException(podId));
    }

    @Transactional
    public PodResponse createPod(String profileId, CreatePodRequest request) {
        String id = request.name()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9가-힣]+", "-")
                .replaceAll("(^-|-$)", "");
        if (id.isBlank()) {
            id = "pod-" + System.currentTimeMillis();
        }
        if (existsById(id)) {
            id = id + "-" + System.currentTimeMillis();
        }

        jdbcClient.sql("""
                        INSERT INTO pods (id, name, description, member_count, certified_today, max_members, streak, tag_line, needs_check_in, invite_code)
                        VALUES (:id, :name, :description, 1, 0, :maxMembers, 0, :tagLine, TRUE, :inviteCode)
                        """)
                .param("id", id)
                .param("name", request.name())
                .param("description", request.description())
                .param("maxMembers", request.maxMembers())
                .param("tagLine", request.tagLine())
                .param("inviteCode", inviteCodeFor(id))
                .update();

        insertTags(id, request.tags());
        jdbcClient.sql("""
                        INSERT INTO pod_members (pod_id, profile_id, member_role, streak, checked_in_today)
                        VALUES (:podId, :profileId, '나', 0, FALSE)
                        """)
                .param("podId", id)
                .param("profileId", profileId)
                .update();
        return getPod(id);
    }

    public PodResponse previewJoin(String inviteCode) {
        return jdbcClient.sql("""
                        SELECT id, name, description, member_count, certified_today, max_members,
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
                        rs.getString("invite_code")
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
                        ON DUPLICATE KEY UPDATE member_role = VALUES(member_role)
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
        jdbcClient.sql("""
                        UPDATE pods
                        SET member_count = GREATEST(member_count - 1, 0)
                        WHERE id = :podId
                        """)
                .param("podId", podId)
                .update();
    }

    public List<PodMemberResponse> getMembers(String podId) {
        getPod(podId);
        return jdbcClient.sql("""
                        SELECT p.id, p.name, p.handle, pm.streak, pm.checked_in_today, pm.member_role
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
                        rs.getString("member_role")
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
            String inviteCode
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
                inviteCode
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

    private boolean existsById(String podId) {
        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM pods WHERE id = :id")
                .param("id", podId)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    private String inviteCodeFor(String podId) {
        return podId.replaceAll("[^a-zA-Z0-9]", "").toUpperCase(Locale.ROOT) + "01";
    }
}
