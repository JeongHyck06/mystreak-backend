package mystreak.backend.stats;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import mystreak.backend.config.OpenApiConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @Operation(summary = "내 스트릭 통계를 조회합니다")
    @GetMapping("/me")
    public StatsResponse getMyStats(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        return statsService.getMyStats(year, month);
    }
}
