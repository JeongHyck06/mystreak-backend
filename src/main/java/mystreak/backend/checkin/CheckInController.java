package mystreak.backend.checkin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import mystreak.backend.auth.AuthService;
import mystreak.backend.config.OpenApiConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class CheckInController {

    private final CheckInService checkInService;
    private final AuthService authService;

    public CheckInController(CheckInService checkInService, AuthService authService) {
        this.checkInService = checkInService;
        this.authService = authService;
    }

    @Operation(summary = "팟 인증 피드를 조회합니다")
    @GetMapping("/api/pods/{podId}/feed")
    public List<CheckInResponse> getPodFeed(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable String podId
    ) {
        return checkInService.getPodFeed(authService.requireUserId(authorization), podId);
    }

    @Operation(summary = "팟에 오늘의 인증을 등록합니다")
    @PostMapping("/api/pods/{podId}/check-ins")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckInResponse createCheckIn(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable String podId,
            @Valid @RequestBody CreateCheckInRequest request
    ) {
        return checkInService.createCheckIn(authService.requireUserId(authorization), podId, request);
    }

    @Operation(summary = "내가 올린 인증을 수정합니다")
    @PatchMapping("/api/check-ins/{checkInId}")
    public CheckInResponse updateCheckIn(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable String checkInId,
            @Valid @RequestBody UpdateCheckInRequest request
    ) {
        return checkInService.updateCheckIn(authService.requireUserId(authorization), checkInId, request);
    }

    @Operation(summary = "내가 올린 인증을 삭제합니다")
    @DeleteMapping("/api/check-ins/{checkInId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCheckIn(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable String checkInId
    ) {
        checkInService.deleteCheckIn(authService.requireUserId(authorization), checkInId);
    }

    @Operation(summary = "다른 멤버의 인증을 체크합니다 (본인 인증은 불가)")
    @PostMapping("/api/check-ins/{checkInId}/checks")
    public CheckInResponse check(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable String checkInId
    ) {
        return checkInService.toggleCheck(authService.requireUserId(authorization), checkInId);
    }

    @Operation(summary = "인증에 좋아요를 토글합니다")
    @PostMapping("/api/check-ins/{checkInId}/likes")
    public CheckInResponse like(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable String checkInId
    ) {
        return checkInService.toggleLike(authService.requireUserId(authorization), checkInId);
    }

    @Operation(summary = "인증의 댓글 목록을 조회합니다")
    @GetMapping("/api/check-ins/{checkInId}/comments")
    public List<CommentResponse> getComments(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable String checkInId
    ) {
        return checkInService.getComments(authService.requireUserId(authorization), checkInId);
    }

    @Operation(summary = "인증에 댓글을 작성합니다")
    @PostMapping("/api/check-ins/{checkInId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse addComment(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable String checkInId,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        return checkInService.addComment(authService.requireUserId(authorization), checkInId, request);
    }
}
