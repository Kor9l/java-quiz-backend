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
 * The first English grammar course: fourteen sections of base-level material, their articles and
 * 84 quiz questions.
 *
 * <p>Loads through the same tables as the backend topics, because a grammar course is the same
 * shape — a topic of sections, an article per section, six questions per section — and reusing
 * them is what hands grammar the quiz, the read state and the level tracks for free. What keeps
 * the two apart is the {@code module} column {@code V15__grammar_module} added; this is the first
 * content to set it to anything but the default.
 *
 * <p>Follows the self-contained layout {@link V6__LoadSqlTopic} established: content under
 * {@code /content/english/grammar/base/} rather than in the shared files V2 reads, because V2 has
 * already run everywhere.
 *
 * <p>A grammar course runs one level end to end, so every section and every question here is
 * {@code BASE}. Both columns default to {@code MIDDLE} — a backend level — so a missing value
 * would not merely be wrong, it would put the row in a pool no English track ever queries.
 * {@code GrammarBaseContentTest} holds the content to an explicit level; this migration refuses
 * to guess one.
 */
public class V16__LoadGrammarBase extends BaseJavaMigration {

    private static final String TOPIC_ID = "grammar-base";
    private static final String BASE = "/content/english/grammar/base/";

    @Override
    public void migrate(Context context) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Connection conn = context.getConnection();
        loadTopic(conn, read(mapper, BASE + "topic.json"));
        loadMaterials(conn, read(mapper, BASE + "materials.json"));
        loadQuestions(conn, read(mapper, BASE + "questions.json"));
    }

    private void loadTopic(Connection conn, JsonNode root) throws Exception {
        JsonNode topic = root.get("topic");
        try (PreparedStatement topicPs = conn.prepareStatement(
                "INSERT INTO topics (id, module, sort_order, name_en, name_ru) VALUES (?, ?, ?, ?, ?)");
             PreparedStatement sectionPs = conn.prepareStatement(
                     "INSERT INTO sections (topic_id, id, sort_order, title_en, title_ru, level, area) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            topicPs.setString(1, topic.get("id").asText());
            topicPs.setString(2, required(topic, "module").toUpperCase());
            topicPs.setInt(3, topic.get("order").asInt());
            topicPs.setString(4, topic.get("name").get("en").asText());
            topicPs.setString(5, topic.get("name").get("ru").asText());
            topicPs.executeUpdate();

            for (JsonNode section : topic.get("sections")) {
                sectionPs.setString(1, topic.get("id").asText());
                sectionPs.setString(2, section.get("id").asText());
                sectionPs.setInt(3, section.get("order").asInt());
                sectionPs.setString(4, section.get("title").get("en").asText());
                sectionPs.setString(5, section.get("title").get("ru").asText());
                sectionPs.setString(6, required(section, "level").toUpperCase());
                // Nullable in the schema for the backend sections that have no area, but a
                // grammar section without one loses the only handle on "all conditionals".
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
                // Where a backend question carries a code listing, a grammar question carries
                // the sentence under discussion — same column, same purpose: the thing the
                // question is about, kept out of the prose so it renders as a block.
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
