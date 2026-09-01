package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.UUID;

/**
 * Loads "2026 part 2 words" — 42 entries from a lesson handout, as one more PUBLIC group.
 *
 * <p>Its own file and its own migration rather than a ninth group inside
 * {@code content/english/words.json}: V10 has already run everywhere, so a group added there
 * would appear on a fresh database and be missing on an existing one. Same reasoning as the
 * topics that ship as {@code content/<topic>/} plus a migration of their own.
 *
 * <p>The handout is condensed rather than copied: each row keeps the phrase itself — not the
 * sentence it was shown in — with the sentence carried over as the example where it earns its
 * place, and the grammar notes left in the handout.
 */
public class V14__LoadWords2026Part2 extends BaseJavaMigration {

    private static final String RESOURCE = "/content/english/words-2026-part-2.json";

    @Override
    public void migrate(Context context) throws Exception {
        JsonNode root;
        try (InputStream in = getClass().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource: " + RESOURCE);
            }
            root = new ObjectMapper().readTree(in);
        }
        Connection conn = context.getConnection();
        try (PreparedStatement groupPs = conn.prepareStatement(
                "INSERT INTO word_groups (id, code, title, group_type, owner_id, sort_order) "
                        + "VALUES (?, ?, ?, 'PUBLIC', NULL, ?)");
             PreparedStatement wordPs = conn.prepareStatement(
                     "INSERT INTO words (id, group_id, sort_order, text, translation, example, is_new, "
                             + "correct_count, incorrect_count) VALUES (?, ?, ?, ?, ?, ?, FALSE, ?, ?)")) {
            for (JsonNode group : root.get("groups")) {
                UUID groupId = UUID.randomUUID();
                groupPs.setObject(1, groupId);
                groupPs.setString(2, group.get("code").asText());
                groupPs.setString(3, group.get("title").asText());
                groupPs.setInt(4, group.get("order").asInt());
                groupPs.addBatch();

                int order = 0;
                for (JsonNode word : group.get("words")) {
                    wordPs.setObject(1, UUID.randomUUID());
                    wordPs.setObject(2, groupId);
                    wordPs.setInt(3, order++);
                    wordPs.setString(4, word.get("text").asText());
                    wordPs.setString(5, word.get("translation").asText());
                    setNullable(wordPs, 6, word.path("example"));
                    wordPs.setInt(7, word.path("correct").asInt(0));
                    wordPs.setInt(8, word.path("incorrect").asInt(0));
                    wordPs.addBatch();
                }
            }
            groupPs.executeBatch();
            wordPs.executeBatch();
        }
    }

    private void setNullable(PreparedStatement ps, int index, JsonNode node) throws Exception {
        if (node == null || node.isMissingNode() || node.isNull()) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, node.asText());
        }
    }
}
