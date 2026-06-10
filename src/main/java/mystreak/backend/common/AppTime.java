package mystreak.backend.common;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;

public final class AppTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private AppTime() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static Timestamp startOfToday() {
        return startOfDay(today());
    }

    public static Timestamp startOfTomorrow() {
        return startOfDay(today().plusDays(1));
    }

    public static LocalDate toAppDate(Timestamp timestamp) {
        return timestamp.toInstant().atZone(ZONE).toLocalDate();
    }

    private static Timestamp startOfDay(LocalDate date) {
        return Timestamp.from(date.atStartOfDay(ZONE).toInstant());
    }
}
