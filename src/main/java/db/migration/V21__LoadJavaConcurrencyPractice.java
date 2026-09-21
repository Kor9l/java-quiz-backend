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
 * Loads the concurrency exercises: eleven tasks on the Java track, one for each section of
 * {@code java-concurrency} that a sandbox can grade.
 *
 * <p>A second file and a second migration rather than more tasks in {@code java.json}, for the
 * reason {@link V6__LoadSqlTopic} states for topics: {@link V13__LoadJavaPractice} has already
 * run on every database, so anything appended to the file it read would never arrive. The track
 * is still one track — the {@code track} column says {@code java} here too, and the sort orders
 * carry on from 6 where the Java Core exercises stopped, so the concurrency ones list after them
 * inside each difficulty.
 *
 * <p>Nothing was loadable here until the sandbox could survive the content: a submission that
 * deadlocks is the ordinary case for these exercises, and until execution moved into a child
 * JVM the only answer to one was to abandon a thread. Every reference solution has been compiled
 * and run by {@code JavaPracticeContentTest} before this migration ever sees it.
 */
public class V21__LoadJavaConcurrencyPractice extends BaseJavaMigration {

    private static final String CONTENT = "/content/practice/java-concurrency.json";

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
