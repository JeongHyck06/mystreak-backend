package mystreak.backend.checkin;

public class CheckInNotFoundException extends RuntimeException {

    public CheckInNotFoundException(String checkInId) {
        super("Check-in not found: " + checkInId);
    }
}
