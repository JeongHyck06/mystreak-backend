package mystreak.backend.stats;

import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StatsService {

    public StatsResponse getMyStats(Integer year, Integer month) {
        YearMonth now = YearMonth.now();
        YearMonth selectedMonth = YearMonth.of(
                year == null ? now.getYear() : year,
                month == null ? now.getMonthValue() : month
        );

        return new StatsResponse(
                0,
                0,
                0,
                7,
                0,
                0,
                0,
                0,
                selectedMonth.getMonthValue(),
                selectedMonth.getYear(),
                List.of(),
                null
        );
    }
}
