package mystreak.backend.checkin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CheckInService {

    private final Map<String, CheckInResponse> checkIns = new LinkedHashMap<>();

    public List<CheckInResponse> getPodFeed(String podId) {
        return checkIns.values()
                .stream()
                .filter(checkIn -> checkIn.podId().equals(podId))
                .toList();
    }

    public CheckInResponse createCheckIn(String podId, CreateCheckInRequest request) {
        String id = "feed-" + (checkIns.size() + 1);
        CheckInResponse checkIn = new CheckInResponse(
                id,
                podId,
                "me",
                "방금 전",
                request.text(),
                request.mediaUrl(),
                0,
                0,
                false
        );
        put(checkIn);
        return checkIn;
    }

    public CheckReactionResponse check(String checkInId) {
        CheckInResponse current = getCheckIn(checkInId);
        if (current.checkedByMe()) {
            return new CheckReactionResponse(current.id(), true, current.likes());
        }

        CheckInResponse checked = new CheckInResponse(
                current.id(),
                current.podId(),
                current.author(),
                current.meta(),
                current.text(),
                current.mediaUrl(),
                current.likes() + 1,
                current.comments(),
                true
        );
        put(checked);
        return new CheckReactionResponse(checked.id(), checked.checkedByMe(), checked.likes());
    }

    public CheckInResponse getCheckIn(String checkInId) {
        CheckInResponse checkIn = checkIns.get(checkInId);
        if (checkIn == null) {
            throw new CheckInNotFoundException(checkInId);
        }
        return checkIn;
    }

    public List<CheckInResponse> allCheckIns() {
        return new ArrayList<>(checkIns.values());
    }

    private void put(CheckInResponse checkIn) {
        checkIns.put(checkIn.id(), checkIn);
    }
}
