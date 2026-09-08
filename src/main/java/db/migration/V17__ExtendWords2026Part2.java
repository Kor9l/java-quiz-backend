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
 * 44 more words for the existing "2026 part 2 words" group: the tail of Wordlist Unit 1A that
 * the first batch stopped short of, plus both vocabulary tables of the 1.2 Project Planning &
 * Preparation handout — Scheduling & Time Management and Budget & Resources.
 *
 * <p>Appends to a group rather than creating one, which is what makes this migration different
 * in shape from {@link V14__LoadWords2026Part2}: the group already exists everywhere V14 ran, so
 * the words cannot simply be added to {@code words-2026-part-2.json} — a fresh database would
 * load them there and an existing one would never see them. The content file therefore names the
 * group by its stable {@code code} instead of describing one, and the rows land after whatever
 * the group already holds.
 *
 * <p>The handout is condensed the same way V14 condensed the first one: the phrase itself as the
 * entry rather than the sentence it was shown in, that sentence kept as the example where it
 * earns its place, and the pronunciation and grammar notes left in the handout.
 */
public class V17__ExtendWords2026Part2 extends BaseJavaMigration {

    private static final String RESOURCE = "/content/english/words-2026-part-2-extra.json";

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
     * The group is looked up rather than assumed, because V14 generates its id at migration time
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
     * or removed words in the group since V14, and a colliding sort_order would interleave the
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
