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

    @Operation(summary = "내가 참여 중인 팟 목록을 조회합니다")
    @GetMapping
    public List<PodResponse> getMyPods() {
        return podService.getMyPods();
    }

    @Operation(summary = "팟 상세 정보를 조회합니다")
    @GetMapping("/{podId}")
    public PodResponse getPod(@PathVariable String podId) {
        return podService.getPod(podId);
    }

    @Operation(summary = "새 팟을 생성합니다")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PodResponse createPod(@Valid @RequestBody CreatePodRequest request) {
        return podService.createPod(request);
    }

    @Operation(summary = "초대 코드로 가입할 팟을 미리 조회합니다")
    @GetMapping("/join-preview")
    public PodResponse previewJoin(@RequestParam String inviteCode) {
        return podService.previewJoin(inviteCode);
    }

    @Operation(summary = "초대 코드로 팟에 가입합니다")
    @PostMapping("/join")
    public PodResponse joinPod(@Valid @RequestBody JoinPodRequest request) {
        return podService.joinPod(request);
    }

    @Operation(summary = "현재 사용자가 팟에서 나갑니다")
    @DeleteMapping("/{podId}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leavePod(@PathVariable String podId) {
        podService.leavePod(podId);
    }

    @Operation(summary = "팟 멤버 목록을 조회합니다")
    @GetMapping("/{podId}/members")
    public List<PodMemberResponse> getMembers(@PathVariable String podId) {
        return podService.getMembers(podId);
    }

    @Operation(summary = "핸들로 팟 멤버를 초대합니다")
    @PostMapping("/{podId}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    public InviteResponse inviteMember(
            @PathVariable String podId,
            @Valid @RequestBody InviteMemberRequest request
    ) {
        return podService.inviteMember(podId, request);
    }
}
