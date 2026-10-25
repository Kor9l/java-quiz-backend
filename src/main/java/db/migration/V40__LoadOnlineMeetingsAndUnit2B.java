package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.List;
import java.util.UUID;

/**
 * Loads two PUBLIC groups, one per handout, each titled with the date it was added and the
 * handout's file name, as {@link V38__LoadWordlistUnit1D} titles its own: "25.10.26 Phrases for
 * Online Meetings" and "25.10.26 Wordlist_Unit 2B_DevelopYourReading".
 *
 * <p>Their own files and their own migration for the reason {@link V14__LoadWords2026Part2} spells
 * out: {@code content/english/words.json} has already been loaded everywhere by V10, so a group
 * added there would reach a fresh database and never an existing one.
 *
 * <p>The meetings handout puts several phrases in one numbered row; each is an entry of its own
 * here, as are the glosses the rows carry ("on the horizon", "to pull up", "a hard stop"). The
 * Check Yourself gap-fills and the discussion questions repeat the same phrases and are not loaded.
 */
public class V40__LoadOnlineMeetingsAndUnit2B extends BaseJavaMigration {

    private static final List<String> RESOURCES = List.of(
            "/content/english/words-online-meetings.json",
            "/content/english/words-unit-2b.json");

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();
        try (PreparedStatement groupPs = conn.prepareStatement(
                "INSERT INTO word_groups (id, code, title, group_type, owner_id, sort_order) "
                        + "VALUES (?, ?, ?, 'PUBLIC', NULL, ?)");
             PreparedStatement wordPs = conn.prepareStatement(
                     "INSERT INTO words (id, group_id, sort_order, text, translation, example, is_new, "
                             + "correct_count, incorrect_count) VALUES (?, ?, ?, ?, ?, ?, FALSE, 0, 0)")) {
            for (String resource : RESOURCES) {
                for (JsonNode group : read(resource).get("groups")) {
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
            }
            groupPs.executeBatch();
            wordPs.executeBatch();
        }
    }

    private JsonNode read(String resource) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource: " + resource);
            }
            return new ObjectMapper().readTree(in);
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
