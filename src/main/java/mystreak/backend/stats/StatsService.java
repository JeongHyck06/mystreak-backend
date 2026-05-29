package mystreak.backend.stats;

import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StatsService {

    private static final List<Integer> HEATMAP = List.of(
            0, 2, 4, 3, 4, 5, 0, 1, 0, 0, 0, 1, 0, 0,
            0, 3, 4, 5, 4, 3, 0, 1, 0, 2, 5, 0, 4, 3,
            1, 4, 0, 0, 2, 0, 0, 3, 5, 4, 5, 4, 0, 0,
            3, 4, 5, 4, 5, 4, 0, 1, 0, 0, 0, 0
    );

    public StatsResponse getMyStats(Integer year, Integer month) {
        YearMonth selectedMonth = YearMonth.of(
                year == null ? 2026 : year,
                month == null ? 5 : month
        );

        return new StatsResponse(
                27,
                42,
                6,
                7,
                146,
                3,
                87,
                26,
                selectedMonth.getMonthValue(),
                selectedMonth.getYear(),
                HEATMAP,
                "연속 3주 완주 클럽하우스"
        );
    }
}
