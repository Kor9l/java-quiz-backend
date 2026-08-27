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
 * Loads the bundled practice exercises. A Java migration for the same reason as
 * {@link V2__LoadContent}: the content is SQL, and Flyway would try to interpret parts of it
 * if it went through a plain script.
 */
public class V5__LoadPractice extends BaseJavaMigration {

    private static final String[] TRACKS = {"sql"};

    @Override
    public void migrate(Context context) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Connection conn = context.getConnection();
        for (String track : TRACKS) {
            JsonNode root = read(mapper, "/content/practice/" + track + ".json");
            loadDatasets(conn, root);
            loadTasks(conn, root, root.get("track").asText());
        }
    }

    private void loadDatasets(Connection conn, JsonNode root) throws Exception {
        try (PreparedStatement datasetPs = conn.prepareStatement(
                "INSERT INTO practice_datasets (id, sort_order, title_en, title_ru, description_en, description_ru) "
                        + "VALUES (?, ?, ?, ?, ?, ?)");
             PreparedStatement setupPs = conn.prepareStatement(
                     "INSERT INTO practice_dataset_statements (dataset_id, sort_order, sql_text) VALUES (?, ?, ?)")) {
            for (JsonNode dataset : root.get("datasets")) {
                String id = dataset.get("id").asText();
                datasetPs.setString(1, id);
                datasetPs.setInt(2, dataset.get("order").asInt());
                datasetPs.setString(3, dataset.get("title").get("en").asText());
                datasetPs.setString(4, dataset.get("title").get("ru").asText());
                datasetPs.setString(5, dataset.get("description").get("en").asText());
                datasetPs.setString(6, dataset.get("description").get("ru").asText());
                datasetPs.addBatch();

                int order = 0;
                for (JsonNode statement : dataset.get("setup")) {
                    setupPs.setString(1, id);
                    setupPs.setInt(2, order++);
                    setupPs.setString(3, statement.asText());
                    setupPs.addBatch();
                }
            }
            datasetPs.executeBatch();
            setupPs.executeBatch();
        }
    }

    private void loadTasks(Connection conn, JsonNode root, String track) throws Exception {
        try (PreparedStatement taskPs = conn.prepareStatement(
                "INSERT INTO practice_tasks (id, track, dataset_id, difficulty, sort_order, topic_id, section_id, "
                        + "title_en, title_ru, statement_en, statement_ru, hint_en, hint_ru, starter_sql, "
                        + "solution_sql, order_matters, explanation_en, explanation_ru) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
             PreparedStatement sourcePs = conn.prepareStatement(
                     "INSERT INTO practice_task_sources (task_id, sort_order, title, url) VALUES (?, ?, ?, ?)")) {
            for (JsonNode task : root.get("tasks")) {
                String id = task.get("id").asText();
                taskPs.setString(1, id);
                taskPs.setString(2, track);
                taskPs.setString(3, task.get("dataset").asText());
                taskPs.setString(4, task.get("difficulty").asText().toUpperCase());
                taskPs.setInt(5, task.get("order").asInt());
                setNullable(taskPs, 6, task.path("topic"));
                setNullable(taskPs, 7, task.path("section"));
                taskPs.setString(8, task.get("title").get("en").asText());
                taskPs.setString(9, task.get("title").get("ru").asText());
                taskPs.setString(10, task.get("statement").get("en").asText());
                taskPs.setString(11, task.get("statement").get("ru").asText());
                setNullable(taskPs, 12, task.path("hint").path("en"));
                setNullable(taskPs, 13, task.path("hint").path("ru"));
                setNullable(taskPs, 14, task.path("starter"));
                taskPs.setString(15, task.get("solution").asText());
                taskPs.setBoolean(16, task.get("orderMatters").asBoolean());
                taskPs.setString(17, task.get("explanation").get("en").asText());
                taskPs.setString(18, task.get("explanation").get("ru").asText());
                taskPs.addBatch();

                JsonNode sources = task.get("sources");
                if (sources != null && sources.isArray()) {
                    int order = 0;
                    for (JsonNode source : sources) {
                        sourcePs.setString(1, id);
                        sourcePs.setInt(2, order++);
                        sourcePs.setString(3, source.get("title").asText());
                        sourcePs.setString(4, source.get("url").asText());
                        sourcePs.addBatch();
                    }
                }
            }
            taskPs.executeBatch();
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

    private JsonNode read(ObjectMapper mapper, String path) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource: " + path);
            }
            return mapper.readTree(in);
        }
    }
}
