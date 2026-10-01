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
 * Loads one PUBLIC group, "01.10.26 Wordlist_Unit 1D": the English in Action phrases, the healthy
 * lifestyle collocations and the conversation phrases of that handout.
 *
 * <p>Its own file and its own migration for the reason {@link V14__LoadWords2026Part2} spells
 * out: {@code content/english/words.json} has already been loaded everywhere by V10, so a group
 * added there would reach a fresh database and never an existing one.
 *
 * <p>The handout is condensed the way {@link V36__LoadPersonalityWords} condenses its own: the
 * phrase as the entry, the handout's sentence kept as the example where it gave one, and the
 * pronunciation left in {@code text} in the bracketed UK form. The Check Yourself gap-fill and the
 * discussion questions repeat the same phrases and are not loaded.
 */
public class V38__LoadWordlistUnit1D extends BaseJavaMigration {

    private static final String RESOURCE = "/content/english/words-unit-1d.json";

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
                             + "correct_count, incorrect_count) VALUES (?, ?, ?, ?, ?, ?, FALSE, 0, 0)")) {
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
