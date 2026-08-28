package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * Adds SQL as a study topic: sections, articles and quiz questions, alongside the practice
 * exercises that drill the same sections.
 *
 * <p>Its content lives under {@code /content/sql/} rather than in the shared
 * {@code topics.json} and {@code questions/} directories that {@link V2__LoadContent} reads.
 * Those files describe the five topics that shipped first, and V2 has already run everywhere;
 * adding SQL to them would load it on a fresh database and skip it on an existing one.
 */
public class V6__LoadSqlTopic extends BaseJavaMigration {

    private static final String TOPIC_ID = "sql";

    @Override
    public void migrate(Context context) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Connection conn = context.getConnection();
        loadTopic(conn, read(mapper, "/content/sql/topic.json"));
        loadMaterials(conn, read(mapper, "/content/sql/materials.json"));
        loadQuestions(conn, read(mapper, "/content/sql/questions.json"));
    }

    private void loadTopic(Connection conn, JsonNode root) throws Exception {
        JsonNode topic = root.get("topic");
        try (PreparedStatement topicPs = conn.prepareStatement(
                "INSERT INTO topics (id, sort_order, name_en, name_ru) VALUES (?, ?, ?, ?)");
             PreparedStatement sectionPs = conn.prepareStatement(
                     "INSERT INTO sections (topic_id, id, sort_order, title_en, title_ru) VALUES (?, ?, ?, ?, ?)")) {
            topicPs.setString(1, topic.get("id").asText());
            topicPs.setInt(2, topic.get("order").asInt());
            topicPs.setString(3, topic.get("name").get("en").asText());
            topicPs.setString(4, topic.get("name").get("ru").asText());
            topicPs.executeUpdate();

            for (JsonNode section : topic.get("sections")) {
                sectionPs.setString(1, topic.get("id").asText());
                sectionPs.setString(2, section.get("id").asText());
                sectionPs.setInt(3, section.get("order").asInt());
                sectionPs.setString(4, section.get("title").get("en").asText());
                sectionPs.setString(5, section.get("title").get("ru").asText());
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
                     "INSERT INTO material_sources (topic_id, section_id, sort_order, title, url) VALUES (?, ?, ?, ?, ?)")) {
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
                "INSERT INTO questions (id, topic_id, section_id, difficulty, text_en, text_ru, code, "
                        + "explanation_en, explanation_ru) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
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
                qPs.setString(5, question.get("text").get("en").asText());
                qPs.setString(6, question.get("text").get("ru").asText());
                JsonNode code = question.get("code");
                qPs.setString(7, code == null || code.isNull() ? null : code.asText());
                qPs.setString(8, question.get("explanation").get("en").asText());
                qPs.setString(9, question.get("explanation").get("ru").asText());
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

    private JsonNode read(ObjectMapper mapper, String path) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource: " + path);
            }
            return mapper.readTree(in);
        }
    }
}
