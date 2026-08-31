package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * Adds Java Concurrency as a study topic: twelve sections, their articles and 72 quiz questions.
 *
 * <p>Follows the self-contained layout {@link V6__LoadSqlTopic} established — content under
 * {@code /content/java-concurrency/} rather than in the shared files V2 reads, because V2 has
 * already run everywhere and editing its sources would load a topic on a fresh database while
 * skipping it on an existing one.
 *
 * <p>Unlike the earlier topics this one carries the career {@code level} added by
 * {@code V7__levels} on both sections and questions. The column defaults to {@code MIDDLE}, so a
 * missing level would load silently as middle; {@code JavaConcurrencyTopicContentTest} is what
 * holds the content to an explicit value on every row.
 */
public class V8__LoadJavaConcurrencyTopic extends BaseJavaMigration {

    private static final String TOPIC_ID = "java-concurrency";
    private static final String BASE = "/content/" + TOPIC_ID + "/";

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
                "INSERT INTO topics (id, sort_order, name_en, name_ru) VALUES (?, ?, ?, ?)");
             PreparedStatement sectionPs = conn.prepareStatement(
                     "INSERT INTO sections (topic_id, id, sort_order, title_en, title_ru, level) "
                             + "VALUES (?, ?, ?, ?, ?, ?)")) {
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
                sectionPs.setString(6, level(section));
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
                qPs.setString(5, level(question));
                qPs.setString(6, question.get("text").get("en").asText());
                qPs.setString(7, question.get("text").get("ru").asText());
                JsonNode code = question.get("code");
                qPs.setString(8, code == null || code.isNull() ? null : code.asText());
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
     * Fails the migration rather than letting the column default quietly turn missing content into
     * middle-level content.
     */
    private String level(JsonNode node) {
        JsonNode level = node.get("level");
        if (level == null || level.isNull() || level.asText().isBlank()) {
            throw new IllegalStateException("Missing level in " + TOPIC_ID + " content: " + node.get("id"));
        }
        return level.asText().toUpperCase();
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
