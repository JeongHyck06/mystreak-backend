package mystreak.backend.stats;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import mystreak.backend.auth.AuthService;
import mystreak.backend.config.OpenApiConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class StatsController {

    private final StatsService statsService;
    private final AuthService authService;

    public StatsController(StatsService statsService, AuthService authService) {
        this.statsService = statsService;
        this.authService = authService;
    }

    @Operation(summary = "내 스트릭 통계를 조회합니다")
    @GetMapping("/me")
    public StatsResponse getMyStats(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        return statsService.getMyStats(authService.requireUserId(authorization), year, month);
    }
}
