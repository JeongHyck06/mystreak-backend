package mystreak.backend.pod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PodService {

    private final Map<String, PodResponse> pods = new LinkedHashMap<>();

    public PodService() {
        seedPods();
    }

    public List<PodResponse> getMyPods() {
        return new ArrayList<>(pods.values());
    }

    public PodResponse getPod(String podId) {
        PodResponse pod = pods.get(podId);
        if (pod == null) {
            throw new PodNotFoundException(podId);
        }
        return pod;
    }

    public PodResponse createPod(CreatePodRequest request) {
        String id = request.name()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9가-힣]+", "-")
                .replaceAll("(^-|-$)", "");
        if (id.isBlank()) {
            id = "pod-" + (pods.size() + 1);
        }

        PodResponse pod = new PodResponse(
                id,
                request.name(),
                request.description(),
                1,
                0,
                request.maxMembers(),
                0,
                request.tagLine(),
                List.copyOf(request.tags()),
                true,
                inviteCodeFor(id)
        );
        pods.put(id, pod);
        return pod;
    }

    public PodResponse previewJoin(String inviteCode) {
        return pods.values()
                .stream()
                .filter(pod -> pod.inviteCode().equalsIgnoreCase(inviteCode))
                .findFirst()
                .orElseThrow(() -> new PodNotFoundException(inviteCode));
    }

    public PodResponse joinPod(JoinPodRequest request) {
        PodResponse pod = previewJoin(request.inviteCode());
        PodResponse joined = new PodResponse(
                pod.id(),
                pod.name(),
                pod.description(),
                Math.min(pod.memberCount() + 1, pod.maxMembers()),
                pod.certifiedToday(),
                pod.maxMembers(),
                pod.streak(),
                pod.tagLine(),
                pod.tags(),
                pod.needsCheckIn(),
                pod.inviteCode()
        );
        pods.put(joined.id(), joined);
        return joined;
    }

    public void leavePod(String podId) {
        getPod(podId);
    }

    public List<PodMemberResponse> getMembers(String podId) {
        PodResponse pod = getPod(podId);
        return List.of(
                new PodMemberResponse("me", "김다혜", "@doitall", pod.streak(), true, "나"),
                new PodMemberResponse("member-1", "이서정", "@seojung.lee", pod.streak() + 1, false, "멤버"),
                new PodMemberResponse("member-2", "박지수", "@jisu.park", pod.streak() + 2, false, "멤버")
        );
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

    private void seedPods() {
        put(new PodResponse("running", "새벽 5시 러닝 크루", "아침 5시, 함께 달립니다. 날씨에 구애받지 말고 우선 나가세요.", 248, 6, 8, 12, "운동 · 사진 인증", List.of("#러닝", "#새벽기상", "#운동"), false, "ABC123"));
        put(new PodResponse("english", "매일매일 영어 30분", "하루 30분 영어 루틴을 인증해요.", 56, 5, 7, 8, "학습 · 타이머 인증", List.of("#영어", "#공부"), true, "ENG030"));
        put(new PodResponse("reading", "책가족 독서 모임", "매일 읽은 페이지를 공유하고 서로 응원해요.", 72, 8, 9, 34, "독서 · 한 줄 기록", List.of("#독서", "#기록"), false, "BOOK01"));
    }

    private void put(PodResponse pod) {
        pods.put(pod.id(), pod);
    }

    private String inviteCodeFor(String podId) {
        return podId.replaceAll("[^a-zA-Z0-9]", "").toUpperCase(Locale.ROOT) + "01";
    }
}
