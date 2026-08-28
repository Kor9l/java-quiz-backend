package db.migration;

import com.korl.javaquiz.security.PasswordHasher;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.util.UUID;

public class V3__SeedAdmin extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V3__SeedAdmin.class);
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Override
    public void migrate(Context context) throws Exception {
        // Flyway instantiates this class itself, so the hasher is called statically rather
        // than injected — a @Inject field here would simply stay null.
        String hash = PasswordHasher.hash(adminPassword());
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

    /**
     * A hardcoded password would be an open door on a public deployment. Without an explicit
     * value the account still exists, but only whoever reads this one log line can use it.
     */
    private String adminPassword() {
        String configured = System.getenv("ADMIN_INITIAL_PASSWORD");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String generated = UUID.randomUUID().toString();
        log.warn("ADMIN_INITIAL_PASSWORD is not set — seeding admin@javaquiz.local with the "
                + "generated password {} . Change it after the first sign-in.", generated);
        return generated;
    }
}
