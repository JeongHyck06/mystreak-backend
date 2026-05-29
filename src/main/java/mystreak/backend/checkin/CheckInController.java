package mystreak.backend.checkin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import mystreak.backend.config.OpenApiConfig;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class CheckInController {

    private final CheckInService checkInService;

    public CheckInController(CheckInService checkInService) {
        this.checkInService = checkInService;
    }

    @Operation(summary = "List pod check-in feed")
    @GetMapping("/api/pods/{podId}/feed")
    public List<CheckInResponse> getPodFeed(@PathVariable String podId) {
        return checkInService.getPodFeed(podId);
    }

    @Operation(summary = "Create a pod check-in")
    @PostMapping("/api/pods/{podId}/check-ins")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckInResponse createCheckIn(
            @PathVariable String podId,
            @Valid @RequestBody CreateCheckInRequest request
    ) {
        return checkInService.createCheckIn(podId, request);
    }

    @Operation(summary = "Check another member's check-in")
    @PostMapping("/api/check-ins/{checkInId}/checks")
    public CheckReactionResponse check(@PathVariable String checkInId) {
        return checkInService.check(checkInId);
    }
}
