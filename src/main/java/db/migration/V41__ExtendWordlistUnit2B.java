package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.UUID;

/**
 * 47 more words for the existing "25.10.26 Wordlist_Unit 2B_DevelopYourReading" group from the
 * updated Unit 2B handout: the Running Wild table, its B2+/C1 collocations and the phrases for
 * giving an opinion.
 *
 * <p>Appends to the group V40 created, for the reason {@link V17__ExtendWords2026Part2} spells
 * out: V40 has already run, so words added to {@code words-unit-2b.json} would never reach an
 * existing database. The content file names the group by its {@code code} and the rows land after
 * whatever the group already holds.
 *
 * <p>Rows the group already carries from the Develop Your Reading table are left out (to tackle a
 * problem, to carry out research, to take action, to crack down on), as is the handout's second
 * copy of "If you ask me". Rows holding two phrases (a scheme / a schema, to spend time on / doing)
 * become two entries. The Check Yourself gap-fills and the discussion questions are not loaded.
 */
public class V41__ExtendWordlistUnit2B extends BaseJavaMigration {

    private static final String RESOURCE = "/content/english/words-unit-2b-extra.json";

    @Override
    public void migrate(Context context) throws Exception {
        JsonNode root;
        try (InputStream in = getClass().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource: " + RESOURCE);
            }
            root = new ObjectMapper().readTree(in);
        }
        String code = root.get("groupCode").asText();
        Connection conn = context.getConnection();
        UUID groupId = groupId(conn, code);
        int order = nextSortOrder(conn, groupId);

        try (PreparedStatement wordPs = conn.prepareStatement(
                "INSERT INTO words (id, group_id, sort_order, text, translation, example, is_new, "
                        + "correct_count, incorrect_count) VALUES (?, ?, ?, ?, ?, ?, FALSE, 0, 0)")) {
            for (JsonNode word : root.get("words")) {
                wordPs.setObject(1, UUID.randomUUID());
                wordPs.setObject(2, groupId);
                wordPs.setInt(3, order++);
                wordPs.setString(4, word.get("text").asText());
                wordPs.setString(5, word.get("translation").asText());
                setNullable(wordPs, 6, word.path("example"));
                wordPs.addBatch();
            }
            wordPs.executeBatch();
        }
    }

    /**
     * The group is looked up rather than assumed, because V40 generates its id at migration time
     * and so every database has a different one.
     */
    private UUID groupId(Connection conn, String code) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM word_groups WHERE code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("No word group with code " + code);
                }
                return rs.getObject(1, UUID.class);
            }
        }
    }

    /**
     * Read from the database rather than counted from the content file: an admin may have added
     * or removed words in the group since V40, and a colliding sort_order would interleave the
     * new rows with the old ones.
     */
    private int nextSortOrder(Connection conn, UUID groupId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM words WHERE group_id = ?")) {
            ps.setObject(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
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
