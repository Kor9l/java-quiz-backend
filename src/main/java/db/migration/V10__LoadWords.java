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
 * Loads the bundled English vocabulary — the corpus that came over from the standalone words
 * app, 462 words in 8 groups.
 *
 * <p>Every group lands as PUBLIC. Four of them were one learner's private groups in the app
 * they came from, but that app numbered its users and this one identifies them by UUID, so
 * there is nobody here to hand them back to; shipping them as content is what keeps them
 * reachable at all. Anything a learner adds from now on is PERSONAL and theirs.
 */
public class V10__LoadWords extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        JsonNode root;
        try (InputStream in = getClass().getResourceAsStream("/content/english/words.json")) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource: /content/english/words.json");
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
