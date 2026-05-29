package mystreak.backend.pod;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import mystreak.backend.config.OpenApiConfig;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pods")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class PodController {

    private final PodService podService;

    public PodController(PodService podService) {
        this.podService = podService;
    }

    @Operation(summary = "List my pods")
    @GetMapping
    public List<PodResponse> getMyPods() {
        return podService.getMyPods();
    }

    @Operation(summary = "Get pod detail")
    @GetMapping("/{podId}")
    public PodResponse getPod(@PathVariable String podId) {
        return podService.getPod(podId);
    }

    @Operation(summary = "Create a pod")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PodResponse createPod(@Valid @RequestBody CreatePodRequest request) {
        return podService.createPod(request);
    }

    @Operation(summary = "Preview pod by invite code")
    @GetMapping("/join-preview")
    public PodResponse previewJoin(@RequestParam String inviteCode) {
        return podService.previewJoin(inviteCode);
    }

    @Operation(summary = "Join a pod")
    @PostMapping("/join")
    public PodResponse joinPod(@Valid @RequestBody JoinPodRequest request) {
        return podService.joinPod(request);
    }

    @Operation(summary = "Leave a pod")
    @DeleteMapping("/{podId}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leavePod(@PathVariable String podId) {
        podService.leavePod(podId);
    }

    @Operation(summary = "List pod members")
    @GetMapping("/{podId}/members")
    public List<PodMemberResponse> getMembers(@PathVariable String podId) {
        return podService.getMembers(podId);
    }

    @Operation(summary = "Invite a member to a pod")
    @PostMapping("/{podId}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    public InviteResponse inviteMember(
            @PathVariable String podId,
            @Valid @RequestBody InviteMemberRequest request
    ) {
        return podService.inviteMember(podId, request);
    }
}
