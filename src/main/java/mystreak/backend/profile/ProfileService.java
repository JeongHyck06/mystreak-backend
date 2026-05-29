package mystreak.backend.profile;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class ProfileService {

    private final JdbcClient jdbcClient;

    public ProfileService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public ProfileResponse getMyProfile() {
        return jdbcClient.sql("""
                        SELECT id, name, handle, email, bio, current_streak, best_streak, total_checks, trophies
                        FROM profiles
                        WHERE id = :id
                        """)
                .param("id", "me")
                .query((rs, rowNum) -> new ProfileResponse(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("handle"),
                        rs.getString("email"),
                        rs.getString("bio"),
                        rs.getInt("current_streak"),
                        rs.getInt("best_streak"),
                        rs.getInt("total_checks"),
                        rs.getInt("trophies")
                ))
                .optional()
                .orElse(new ProfileResponse("me", "", "", "", "", 0, 0, 0, 0));
    }

    public ProfileResponse updateMyProfile(UpdateProfileRequest request) {
        ProfileResponse current = getMyProfile();
        int updatedRows = jdbcClient.sql("""
                        UPDATE profiles
                        SET name = :name, handle = :handle, bio = :bio
                        WHERE id = :id
                        """)
                .param("id", current.id())
                .param("name", request.name())
                .param("handle", request.handle())
                .param("bio", request.bio())
                .update();

        if (updatedRows == 0) {
            jdbcClient.sql("""
                            INSERT INTO profiles (id, name, handle, email, bio)
                            VALUES (:id, :name, :handle, :email, :bio)
                            """)
                    .param("id", current.id())
                    .param("name", request.name())
                    .param("handle", request.handle())
                    .param("email", current.email())
                    .param("bio", request.bio())
                    .update();
        }

        return getMyProfile();
    }
}
