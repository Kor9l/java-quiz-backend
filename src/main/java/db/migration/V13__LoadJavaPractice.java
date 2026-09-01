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
 * Loads the bundled Java exercises. A Java migration for the same reason as
 * {@link V5__LoadPractice}: the content is source code, and Flyway would try to interpret
 * parts of it if it went through a plain script.
 *
 * <p>Every solution here has already been compiled and run by {@code JavaPracticeContentTest},
 * which is what keeps this migration from being the place a broken exercise is discovered —
 * it only gets to run once, against a real database.
 */
public class V13__LoadJavaPractice extends BaseJavaMigration {

    private static final String CONTENT = "/content/practice/java.json";

    @Override
    public void migrate(Context context) throws Exception {
        JsonNode root = read(new ObjectMapper());
        loadTasks(context.getConnection(), root, root.get("track").asText());
    }

    private void loadTasks(Connection conn, JsonNode root, String track) throws Exception {
        try (PreparedStatement taskPs = conn.prepareStatement(
                "INSERT INTO practice_tasks (id, track, difficulty, sort_order, topic_id, section_id, "
                        + "title_en, title_ru, statement_en, statement_ru, hint_en, hint_ru, "
                        + "class_name, starter_code, solution_code, order_matters, "
                        + "explanation_en, explanation_ru) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
             PreparedStatement casePs = conn.prepareStatement(
                     "INSERT INTO practice_task_cases (task_id, sort_order, label, expression) "
                             + "VALUES (?, ?, ?, ?)");
             PreparedStatement sourcePs = conn.prepareStatement(
                     "INSERT INTO practice_task_sources (task_id, sort_order, title, url) VALUES (?, ?, ?, ?)")) {
            for (JsonNode task : root.get("tasks")) {
                String id = task.get("id").asText();
                taskPs.setString(1, id);
                taskPs.setString(2, track);
                taskPs.setString(3, task.get("difficulty").asText().toUpperCase());
                taskPs.setInt(4, task.get("order").asInt());
                setNullable(taskPs, 5, task.path("topic"));
                setNullable(taskPs, 6, task.path("section"));
                taskPs.setString(7, task.get("title").get("en").asText());
                taskPs.setString(8, task.get("title").get("ru").asText());
                taskPs.setString(9, task.get("statement").get("en").asText());
                taskPs.setString(10, task.get("statement").get("ru").asText());
                setNullable(taskPs, 11, task.path("hint").path("en"));
                setNullable(taskPs, 12, task.path("hint").path("ru"));
                taskPs.setString(13, task.get("className").asText());
                setNullable(taskPs, 14, task.path("starter"));
                taskPs.setString(15, task.get("solution").asText());
                // Java cases are always compared in the order the task lists them, so the
                // column the SQL track uses to say so is not a choice here.
                taskPs.setBoolean(16, false);
                taskPs.setString(17, task.get("explanation").get("en").asText());
                taskPs.setString(18, task.get("explanation").get("ru").asText());
                taskPs.addBatch();

                int caseOrder = 0;
                for (JsonNode current : task.get("cases")) {
                    casePs.setString(1, id);
                    casePs.setInt(2, caseOrder++);
                    casePs.setString(3, current.get("label").asText());
                    casePs.setString(4, current.get("expression").asText());
                    casePs.addBatch();
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
            casePs.executeBatch();
            sourcePs.executeBatch();
        }
    }

    private void setNullable(PreparedStatement ps, int index, JsonNode node) throws Exception {
        if (node == null || node.isMissingNode() || node.isNull()) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, node.asText());
        }
    }

    private JsonNode read(ObjectMapper mapper) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(CONTENT)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource: " + CONTENT);
            }
            return mapper.readTree(in);
        }
    }
}
