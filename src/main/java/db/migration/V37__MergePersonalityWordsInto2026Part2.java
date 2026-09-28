package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Folds the two groups {@link V36__LoadPersonalityWords} loaded — "Personality traits" and
 * "Verb + noun collocations" — into "2026 part 2 words", taking that group from 86 words to 124.
 * Unit 1C belongs with the rest of Wordlist Unit 1, whose 1A the group already holds; drilling
 * the traits and the collocations apart, which is what V36 split them for, is the part given up.
 *
 * <p>The words are moved, not copied: each row keeps its id and only changes group, so the
 * favourites and the per-word answer history keyed by that id carry over, and nothing ends up
 * listed twice. They land after whatever the group already holds, traits first, each group in
 * the order the trainer showed it.
 *
 * <p>Other rows name the two groups by id, and deleting the groups would leave those references
 * dangling, so they are repointed first. A saved quiz setup, or a round still in progress, that
 * selected either group selects the target instead — dropped, the selection could come out
 * empty, and an empty selection means every group, so a learner drilling the personality words
 * would quietly be drilling the whole bank. The per-group answer counts in the word stats are
 * added into the target's, so the stats screen keeps the answers given while the words lived
 * apart. Finished rounds keep the groups they were played on: that is history, and nothing reads
 * it back.
 *
 * <p>Driven by {@code words-2026-part-2-merge.json} rather than by constants in this class, so
 * that {@code EnglishWordsContentTest} checks the group the trainer will actually show.
 */
public class V37__MergePersonalityWordsInto2026Part2 extends BaseJavaMigration {

    private static final String RESOURCE = "/content/english/words-2026-part-2-merge.json";

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void migrate(Context context) throws Exception {
        JsonNode root;
        try (InputStream in = getClass().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource: " + RESOURCE);
            }
            root = mapper.readTree(in);
        }
        String code = root.get("groupCode").asText();
        Connection conn = context.getConnection();
        UUID target = findGroupId(conn, code);
        if (target == null) {
            throw new IllegalStateException("No word group with code " + code);
        }
        // A group that is already gone was deleted by an admin; there is nothing left to move.
        List<UUID> sources = new ArrayList<>();
        for (JsonNode source : root.get("mergeFrom")) {
            UUID id = findGroupId(conn, source.asText());
            if (id != null) {
                sources.add(id);
            }
        }

        moveWords(conn, sources, target);

        Set<String> merged = new LinkedHashSet<>();
        sources.forEach(id -> merged.add(id.toString()));
        String into = target.toString();
        rewritePayloads(conn,
                "SELECT user_id, payload FROM user_settings",
                "UPDATE user_settings SET payload = ?::jsonb WHERE user_id = ?",
                payload -> repoint(payload, "selectedWordGroups", merged, into));
        rewritePayloads(conn,
                "SELECT id, payload FROM word_quiz_sessions WHERE NOT finished",
                "UPDATE word_quiz_sessions SET payload = ?::jsonb WHERE id = ?",
                payload -> repoint(payload.path("config"), "groupIds", merged, into));
        rewritePayloads(conn,
                "SELECT user_id, payload FROM word_stats",
                "UPDATE word_stats SET payload = ?::jsonb WHERE user_id = ?",
                payload -> foldCounts(payload.path("groups"), merged, into));

        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM word_groups WHERE id = ?")) {
            for (UUID source : sources) {
                ps.setObject(1, source);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Looked up rather than assumed, because V14 and V36 generate their ids at migration time and
     * so every database has different ones. Null when no group carries the code.
     */
    private UUID findGroupId(Connection conn, String code) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM word_groups WHERE code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject(1, UUID.class) : null;
            }
        }
    }

    private void moveWords(Connection conn, List<UUID> sources, UUID target) throws Exception {
        int order = nextSortOrder(conn, target);
        try (PreparedStatement select = conn.prepareStatement(
                     "SELECT id FROM words WHERE group_id = ? ORDER BY sort_order, text");
             PreparedStatement move = conn.prepareStatement(
                     "UPDATE words SET group_id = ?, sort_order = ? WHERE id = ?")) {
            for (UUID source : sources) {
                select.setObject(1, source);
                try (ResultSet rs = select.executeQuery()) {
                    while (rs.next()) {
                        move.setObject(1, target);
                        move.setInt(2, order++);
                        move.setObject(3, rs.getObject(1, UUID.class));
                        move.addBatch();
                    }
                }
            }
            move.executeBatch();
        }
    }

    /**
     * Read from the database rather than taken as 86, for the reason V17 gives: an admin may
     * have added or removed words in the group since, and a colliding sort_order would
     * interleave the moved rows with the ones already there.
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

    /**
     * Applies {@code edit} to every payload the query returns and writes back the ones it
     * reports as changed. Edited as a JSON tree rather than read into the app's payload classes,
     * so every field the edit does not touch goes back exactly as it was stored.
     */
    private void rewritePayloads(Connection conn, String select, String update, Predicate<JsonNode> edit)
            throws Exception {
        Map<Object, String> changed = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(select);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                JsonNode payload = mapper.readTree(rs.getString(2));
                if (edit.test(payload)) {
                    changed.put(rs.getObject(1), mapper.writeValueAsString(payload));
                }
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(update)) {
            for (Map.Entry<Object, String> row : changed.entrySet()) {
                ps.setString(1, row.getValue());
                ps.setObject(2, row.getKey());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Replaces the merged groups with the target in the id list at {@code field}, keeping the
     * order and dropping the repeat that replacing both of them — or one sitting beside the target
     * — would leave.
     */
    private boolean repoint(JsonNode holder, String field, Set<String> merged, String target) {
        JsonNode ids = holder.path(field);
        if (!ids.isArray()) {
            return false;
        }
        Set<String> repointed = new LinkedHashSet<>();
        boolean hit = false;
        for (JsonNode id : ids) {
            boolean moved = merged.contains(id.asText());
            hit |= moved;
            repointed.add(moved ? target : id.asText());
        }
        if (hit) {
            ArrayNode list = ((ObjectNode) holder).putArray(field);
            repointed.forEach(list::add);
        }
        return hit;
    }

    /** Adds the merged groups' answer counts into the target's and drops their own entries. */
    private boolean foldCounts(JsonNode groups, Set<String> merged, String target) {
        if (!(groups instanceof ObjectNode counts)) {
            return false;
        }
        boolean hit = false;
        for (String id : merged) {
            JsonNode from = counts.remove(id);
            if (from == null) {
                continue;
            }
            ObjectNode into = counts.get(target) instanceof ObjectNode existing
                    ? existing
                    : counts.putObject(target);
            for (String field : List.of("answered", "correct")) {
                into.put(field, into.path(field).asInt() + from.path(field).asInt());
            }
            hit = true;
        }
        return hit;
    }
}
