package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.PreparedStatement;
import java.util.UUID;

public class V3__SeedAdmin extends BaseJavaMigration {

    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Override
    public void migrate(Context context) throws Exception {
        String hash = new BCryptPasswordEncoder().encode("admin123");
        try (PreparedStatement userPs = context.getConnection().prepareStatement(
                "INSERT INTO users (id, email, password_hash, display_name, role, auth_provider) VALUES (?, ?, ?, ?, ?, ?)");
             PreparedStatement settingsPs = context.getConnection().prepareStatement(
                     "INSERT INTO user_settings (user_id, payload) VALUES (?, '{}'::jsonb)");
             PreparedStatement statsPs = context.getConnection().prepareStatement(
                     "INSERT INTO user_stats (user_id, total_answered, total_correct, payload) VALUES (?, 0, 0, '{}'::jsonb)");
             PreparedStatement progressPs = context.getConnection().prepareStatement(
                     "INSERT INTO user_progress (user_id, payload) VALUES (?, '{}'::jsonb)")) {
            userPs.setObject(1, ADMIN_ID);
            userPs.setString(2, "admin@javaquiz.local");
            userPs.setString(3, hash);
            userPs.setString(4, "Admin");
            userPs.setString(5, "ADMIN");
            userPs.setString(6, "EMAIL");
            userPs.executeUpdate();

            settingsPs.setObject(1, ADMIN_ID);
            settingsPs.executeUpdate();
            statsPs.setObject(1, ADMIN_ID);
            statsPs.executeUpdate();
            progressPs.setObject(1, ADMIN_ID);
            progressPs.executeUpdate();
        }
    }
}
