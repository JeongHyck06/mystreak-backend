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
        getPod(podId);
        return List.of();
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

    private void put(PodResponse pod) {
        pods.put(pod.id(), pod);
    }

    private String inviteCodeFor(String podId) {
        return podId.replaceAll("[^a-zA-Z0-9]", "").toUpperCase(Locale.ROOT) + "01";
    }
}
