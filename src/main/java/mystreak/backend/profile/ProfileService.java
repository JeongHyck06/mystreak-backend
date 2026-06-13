package mystreak.backend.profile;

import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import mystreak.backend.streak.StreakService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class ProfileService {

    private final JdbcClient jdbcClient;
    private final DataSource dataSource;
    private final StreakService streakService;

    public ProfileService(JdbcClient jdbcClient, DataSource dataSource, StreakService streakService) {
        this.jdbcClient = jdbcClient;
        this.dataSource = dataSource;
        this.streakService = streakService;
    }

    @PostConstruct
    void ensureProfileColumns() {
        if (tableExists("profiles") && !columnExists("profiles", "avatar_url")) {
            jdbcClient.sql("ALTER TABLE profiles ADD COLUMN avatar_url VARCHAR(500)").update();
        }
    }

    public ProfileResponse getMyProfile(String profileId) {
        streakService.recalculateProfile(profileId);
        return jdbcClient.sql("""
                        SELECT id, name, handle, email, bio, avatar_url, current_streak, best_streak, total_checks, trophies
                        FROM profiles
                        WHERE id = :id
                        """)
                .param("id", profileId)
                .query((rs, rowNum) -> new ProfileResponse(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("handle"),
                        rs.getString("email"),
                        rs.getString("bio"),
                        rs.getString("avatar_url"),
                        rs.getInt("current_streak"),
                        rs.getInt("best_streak"),
                        rs.getInt("total_checks"),
                        rs.getInt("trophies")
                ))
                .optional()
                .orElse(new ProfileResponse(profileId, "", "", "", "", null, 0, 0, 0, 0));
    }

    public ProfileResponse updateMyProfile(String profileId, UpdateProfileRequest request) {
        ProfileResponse current = getMyProfile(profileId);
        String nextAvatarUrl = request.avatarUrl() == null ? current.avatarUrl() : blankToNull(request.avatarUrl());
        int updatedRows = jdbcClient.sql("""
                        UPDATE profiles
                        SET name = :name, handle = :handle, bio = :bio, avatar_url = :avatarUrl
                        WHERE id = :id
                        """)
                .param("id", current.id())
                .param("name", request.name())
                .param("handle", request.handle())
                .param("bio", request.bio())
                .param("avatarUrl", nextAvatarUrl)
                .update();

        if (updatedRows == 0) {
            jdbcClient.sql("""
                            INSERT INTO profiles (id, name, handle, email, bio, avatar_url)
                            VALUES (:id, :name, :handle, :email, :bio, :avatarUrl)
                            """)
                    .param("id", current.id())
                    .param("name", request.name())
                    .param("handle", request.handle())
                    .param("email", current.email())
                    .param("bio", request.bio())
                    .param("avatarUrl", nextAvatarUrl)
                    .update();
        }

        return getMyProfile(profileId);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private boolean columnExists(String table, String column) {
        try (Connection connection = dataSource.getConnection();
             ResultSet columns = connection.getMetaData().getColumns(null, null, table, column)) {
            if (columns.next()) {
                return true;
            }
        } catch (SQLException ignored) {
        }

        try (Connection connection = dataSource.getConnection();
             ResultSet columns = connection.getMetaData().getColumns(null, null, table.toUpperCase(), column.toUpperCase())) {
            return columns.next();
        } catch (SQLException e) {
            throw new IllegalStateException("프로필 스키마 확인에 실패했습니다", e);
        }
    }

    private boolean tableExists(String table) {
        try (Connection connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData().getTables(null, null, table, null)) {
            if (tables.next()) {
                return true;
            }
        } catch (SQLException ignored) {
        }

        try (Connection connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData().getTables(null, null, table.toUpperCase(), null)) {
            return tables.next();
        } catch (SQLException e) {
            throw new IllegalStateException("프로필 테이블 확인에 실패했습니다", e);
        }
    }
}
