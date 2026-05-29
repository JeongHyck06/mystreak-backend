package mystreak.backend.profile;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

@Service
public class ProfileService {

    private final AtomicReference<ProfileResponse> profile = new AtomicReference<>(
            new ProfileResponse("me", "", "", "", "", 0, 0, 0, 0)
    );

    public ProfileResponse getMyProfile() {
        return profile.get();
    }

    public ProfileResponse updateMyProfile(UpdateProfileRequest request) {
        return profile.updateAndGet(current -> new ProfileResponse(
                current.id(),
                request.name(),
                request.handle(),
                current.email(),
                request.bio(),
                current.currentStreak(),
                current.bestStreak(),
                current.totalChecks(),
                current.trophies()
        ));
    }
}
