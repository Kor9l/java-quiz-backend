package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;

/**
 * The second homework handout: Present Perfect Simple or Continuous, the passive, and adjectives
 * in -ed / -ing — Murphy units 7-10, 42-43 and 98. Each topic is a theory section with a quiz
 * and a handout section with {@code GAP_FILL} exercises, the second exercise kind of the grammar
 * practice track.
 *
 * <p>It goes into the course {@link V20__LoadGrammarHomework} created rather than into a course
 * of its own: the homework course is where handouts land, in the order lessons hand them out.
 * So there is no topic row here, only sections appended after the ones already there, and the
 * exercises carry on the track's per-difficulty numbering. The content lives in a folder of its
 * own, {@code homework-2/}, because V20 has run against live databases and its files are no
 * longer what any database was loaded from if they change.
 *
 * <p>{@code GrammarHomework2ContentTest} holds the content at build time, including running every
 * accepted answer through the engine that will grade it.
 */
public class V43__LoadGrammarHomework2 extends BaseJavaMigration {

    private static final String TOPIC_ID = "grammar-homework";
    private static final String BASE = "/content/english/grammar/homework-2/";

    @Override
    public void migrate(Context context) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Connection conn = context.getConnection();
        loadSections(conn, read(mapper, BASE + "sections.json"));
        loadMaterials(conn, read(mapper, BASE + "materials.json"));
        loadQuestions(conn, read(mapper, BASE + "questions.json"));
        loadExercises(conn, read(mapper, BASE + "exercises.json"));
    }

    /**
     * Appends the sections to the existing course. The course row is V20's; failing here when it
     * is missing is better than a foreign-key error that names a constraint instead of the cause.
     */
    private void loadSections(Connection conn, JsonNode root) throws Exception {
        String topicId = required(root, "topic");
        if (!TOPIC_ID.equals(topicId)) {
            throw new IllegalStateException("homework-2 belongs to " + TOPIC_ID + ", not " + topicId);
        }
        try (PreparedStatement check = conn.prepareStatement("SELECT 1 FROM topics WHERE id = ?")) {
            check.setString(1, TOPIC_ID);
            try (var rs = check.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Course " + TOPIC_ID + " is missing; V20 should have created it");
                }
            }
        }
        try (PreparedStatement sectionPs = conn.prepareStatement(
                "INSERT INTO sections (topic_id, id, sort_order, title_en, title_ru, level, area) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            for (JsonNode section : root.get("sections")) {
                sectionPs.setString(1, TOPIC_ID);
                sectionPs.setString(2, section.get("id").asText());
                sectionPs.setInt(3, section.get("order").asInt());
                sectionPs.setString(4, section.get("title").get("en").asText());
                sectionPs.setString(5, section.get("title").get("ru").asText());
                // Each handout declares its own level, as in V20: nothing to derive it from.
                sectionPs.setString(6, required(section, "level").toUpperCase());
                sectionPs.setString(7, required(section, "area"));
                sectionPs.addBatch();
            }
            sectionPs.executeBatch();
        }
    }

    private void loadMaterials(Connection conn, JsonNode root) throws Exception {
        try (PreparedStatement mPs = conn.prepareStatement(
                "INSERT INTO material_sections (topic_id, section_id, estimated_minutes, summary_en, summary_ru, "
                        + "body_en, body_ru) VALUES (?, ?, ?, ?, ?, ?, ?)");
             PreparedStatement sPs = conn.prepareStatement(
                     "INSERT INTO material_sources (topic_id, section_id, sort_order, title, url) "
                             + "VALUES (?, ?, ?, ?, ?)")) {
            for (JsonNode section : root.get("sections")) {
                String sectionId = section.get("id").asText();
                mPs.setString(1, TOPIC_ID);
                mPs.setString(2, sectionId);
                mPs.setInt(3, section.get("estimatedMinutes").asInt());
                mPs.setString(4, section.get("summary").get("en").asText());
                mPs.setString(5, section.get("summary").get("ru").asText());
                mPs.setString(6, section.get("body").get("en").asText());
                mPs.setString(7, section.get("body").get("ru").asText());
                mPs.addBatch();

                int sort = 0;
                for (JsonNode source : section.get("sources")) {
                    sPs.setString(1, TOPIC_ID);
                    sPs.setString(2, sectionId);
                    sPs.setInt(3, sort++);
                    sPs.setString(4, source.get("title").asText());
                    sPs.setString(5, source.get("url").asText());
                    sPs.addBatch();
                }
            }
            mPs.executeBatch();
            sPs.executeBatch();
        }
    }

    private void loadQuestions(Connection conn, JsonNode root) throws Exception {
        try (PreparedStatement qPs = conn.prepareStatement(
                "INSERT INTO questions (id, topic_id, section_id, difficulty, level, text_en, text_ru, code, "
                        + "explanation_en, explanation_ru) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
             PreparedStatement oPs = conn.prepareStatement(
                     "INSERT INTO question_options (question_id, option_index, text_en, text_ru, correct) "
                             + "VALUES (?, ?, ?, ?, ?)");
             PreparedStatement sPs = conn.prepareStatement(
                     "INSERT INTO question_sources (question_id, sort_order, url) VALUES (?, ?, ?)")) {
            for (JsonNode question : root.get("questions")) {
                String id = question.get("id").asText();
                qPs.setString(1, id);
                qPs.setString(2, TOPIC_ID);
                qPs.setString(3, question.get("section").asText());
                qPs.setString(4, question.get("difficulty").asText().toUpperCase());
                qPs.setString(5, required(question, "level").toUpperCase());
                qPs.setString(6, question.get("text").get("en").asText());
                qPs.setString(7, question.get("text").get("ru").asText());
                // The sentence under discussion, kept out of the prose so it renders as a block.
                JsonNode code = question.get("code");
                if (code == null || code.isNull()) {
                    qPs.setNull(8, Types.VARCHAR);
                } else {
                    qPs.setString(8, code.asText());
                }
                qPs.setString(9, question.get("explanation").get("en").asText());
                qPs.setString(10, question.get("explanation").get("ru").asText());
                qPs.addBatch();

                int optionIndex = 0;
                for (JsonNode option : question.get("options")) {
                    oPs.setString(1, id);
                    oPs.setInt(2, optionIndex++);
                    oPs.setString(3, option.get("text").get("en").asText());
                    oPs.setString(4, option.get("text").get("ru").asText());
                    oPs.setBoolean(5, option.get("correct").asBoolean());
                    oPs.addBatch();
                }

                JsonNode sources = question.get("sources");
                if (sources != null && sources.isArray()) {
                    int sort = 0;
                    for (JsonNode source : sources) {
                        sPs.setString(1, id);
                        sPs.setInt(2, sort++);
                        sPs.setString(3, source.asText());
                        sPs.addBatch();
                    }
                }
            }
            qPs.executeBatch();
            oPs.executeBatch();
            sPs.executeBatch();
        }
    }

    /**
     * Same insert as V20's. Column list rather than {@code *}, and a
     * narrow one: of everything {@code practice_tasks} holds, a grammar exercise fills the
     * shared half plus {@code kind}, {@code sentence} and {@code case_sensitive}, and leaves the
     * SQL and Java halves alone — which is what {@code practice_tasks_track_shape} checks.
     */
    private void loadExercises(Connection conn, JsonNode root) throws Exception {
        String track = root.get("track").asText();
        try (PreparedStatement taskPs = conn.prepareStatement(
                "INSERT INTO practice_tasks (id, track, kind, difficulty, sort_order, topic_id, section_id, "
                        + "title_en, title_ru, statement_en, statement_ru, hint_en, hint_ru, "
                        + "sentence, case_sensitive, order_matters, explanation_en, explanation_ru) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
             PreparedStatement blankPs = conn.prepareStatement(
                     "INSERT INTO practice_task_blanks (task_id, sort_order, blank_index, accepted) "
                             + "VALUES (?, ?, ?, ?)");
             PreparedStatement sourcePs = conn.prepareStatement(
                     "INSERT INTO practice_task_sources (task_id, sort_order, title, url) VALUES (?, ?, ?, ?)")) {
            for (JsonNode task : root.get("tasks")) {
                String id = task.get("id").asText();
                taskPs.setString(1, id);
                taskPs.setString(2, track);
                taskPs.setString(3, required(task, "kind").toUpperCase());
                taskPs.setString(4, task.get("difficulty").asText().toUpperCase());
                taskPs.setInt(5, task.get("order").asInt());
                // Not optional the way it is on the other two tracks: a grammar exercise takes
                // its level from the section it drills, so a missing link is a missing level.
                taskPs.setString(6, required(task, "topic"));
                taskPs.setString(7, required(task, "section"));
                taskPs.setString(8, task.get("title").get("en").asText());
                taskPs.setString(9, task.get("title").get("ru").asText());
                taskPs.setString(10, task.get("statement").get("en").asText());
                taskPs.setString(11, task.get("statement").get("ru").asText());
                taskPs.setString(12, task.get("hint").get("en").asText());
                taskPs.setString(13, task.get("hint").get("ru").asText());
                taskPs.setString(14, required(task, "sentence"));
                taskPs.setBoolean(15, task.path("caseSensitive").asBoolean(false));
                // The column the SQL track uses for row order, which is not a choice here: word
                // order is the answer itself rather than something about how it is compared.
                taskPs.setBoolean(16, false);
                taskPs.setString(17, task.get("explanation").get("en").asText());
                taskPs.setString(18, task.get("explanation").get("ru").asText());
                taskPs.addBatch();

                int blankOrder = 0;
                for (JsonNode blank : task.get("blanks")) {
                    int index = blank.get("index").asInt();
                    for (JsonNode accepted : blank.get("accepted")) {
                        blankPs.setString(1, id);
                        blankPs.setInt(2, blankOrder++);
                        blankPs.setInt(3, index);
                        blankPs.setString(4, accepted.asText());
                        blankPs.addBatch();
                    }
                }

                JsonNode sources = task.get("sources");
                if (sources != null && sources.isArray()) {
                    int sourceOrder = 0;
                    for (JsonNode source : sources) {
                        sourcePs.setString(1, id);
                        sourcePs.setInt(2, sourceOrder++);
                        sourcePs.setString(3, source.get("title").asText());
                        sourcePs.setString(4, source.get("url").asText());
                        sourcePs.addBatch();
                    }
                }
            }
            taskPs.executeBatch();
            blankPs.executeBatch();
            sourcePs.executeBatch();
        }
    }

    /**
     * Fails the migration rather than letting a column default quietly turn missing content into
     * something plausible-looking.
     */
    private String required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalStateException(
                    "Missing " + field + " in " + TOPIC_ID + " content: " + node.get("id"));
        }
        return value.asText();
    }

    private JsonNode read(ObjectMapper mapper, String path) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource: " + path);
            }
            return mapper.readTree(in);
        }
    }
}
