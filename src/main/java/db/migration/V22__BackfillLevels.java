package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

/**
 * Gives the six pre-V7 topics the levels their content files now carry.
 *
 * <p>{@code V7__levels} added the column with {@code DEFAULT 'MIDDLE'}, which was right for the
 * 294 questions and 49 sections that existed then — every one of them had been written for a
 * middle-level reader. What it left behind was a ladder with one rung occupied: a junior track
 * draws on {@code JUNIOR} and nothing below it, so a learner on that track saw only the one topic
 * tagged since ({@code java-concurrency}), and picking any other returned an empty round. The
 * senior track had the mirror problem — 366 questions in the pool, 20 of them senior material.
 *
 * <p>An {@code UPDATE} rather than an edit to {@code V2} and {@code V6}: both have already run
 * everywhere, and a Flyway migration only gets to run once. The levels themselves live in the
 * content files, so this reads them rather than repeating them — the JSON stays the single place
 * a level is decided, and a later correction is another migration of this shape.
 *
 * <p>How the levels were arrived at is written down in {@code docs/LEARNING_TRACKS.md}: the
 * section level is a judgement about who the article is for, and a question's level is that
 * level shifted by its difficulty — easy one rung down, hard one rung up, clamped at the ends.
 * That is a rule rather than 294 separate verdicts, and it is meant to be overridden per question
 * where it reads wrong; the file is where to do that.
 */
public class V22__BackfillLevels extends BaseJavaMigration {

    /** Topic definitions, and so section levels: five topics in one file, SQL in its own. */
    private static final List<String> TOPIC_FILES = List.of(
            "/content/topics.json",
            "/content/sql/topic.json");

    private static final List<String> QUESTION_FILES = List.of(
            "/content/questions/java-core.json",
            "/content/questions/spring.json",
            "/content/questions/spring-boot.json",
            "/content/questions/hibernate.json",
            "/content/questions/kafka.json",
            "/content/sql/questions.json");

    @Override
    public void migrate(Context context) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Connection conn = context.getConnection();
        updateSections(conn, sectionLevels(mapper));
        updateQuestions(conn, questionLevels(mapper));
    }

    private void updateSections(Connection conn, List<SectionLevel> sections) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE sections SET level = ? WHERE topic_id = ? AND id = ?")) {
            for (SectionLevel section : sections) {
                ps.setString(1, section.level());
                ps.setString(2, section.topicId());
                ps.setString(3, section.sectionId());
                ps.addBatch();
            }
            verify(ps.executeBatch(), sections);
        }
    }

    private void updateQuestions(Connection conn, List<QuestionLevel> questions) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE questions SET level = ? WHERE id = ?")) {
            for (QuestionLevel question : questions) {
                ps.setString(1, question.level());
                ps.setString(2, question.id());
                ps.addBatch();
            }
            verify(ps.executeBatch(), questions);
        }
    }

    /**
     * Every row named by the content has to be there. A level that quietly updated nothing would
     * leave that question in the middle pool for good, and nothing downstream would say so — the
     * column is never null, so the wrong answer looks exactly like the right one.
     */
    private void verify(int[] updated, List<?> intended) {
        for (int i = 0; i < updated.length; i++) {
            if (updated[i] != 1 && updated[i] != PreparedStatement.SUCCESS_NO_INFO) {
                throw new IllegalStateException(
                        "Levels: " + intended.get(i) + " matched " + updated[i] + " rows, expected 1");
            }
        }
    }

    private List<SectionLevel> sectionLevels(ObjectMapper mapper) throws Exception {
        List<SectionLevel> levels = new ArrayList<>();
        for (String file : TOPIC_FILES) {
            JsonNode root = read(mapper, file);
            JsonNode single = root.get("topic");
            if (single != null) {
                collectSections(single, levels, file);
            } else {
                for (JsonNode topic : root.get("topics")) {
                    collectSections(topic, levels, file);
                }
            }
        }
        return levels;
    }

    private void collectSections(JsonNode topic, List<SectionLevel> levels, String file) {
        String topicId = topic.get("id").asText();
        for (JsonNode section : topic.get("sections")) {
            levels.add(new SectionLevel(topicId, section.get("id").asText(),
                    required(section, "level", file)));
        }
    }

    private List<QuestionLevel> questionLevels(ObjectMapper mapper) throws Exception {
        List<QuestionLevel> levels = new ArrayList<>();
        for (String file : QUESTION_FILES) {
            for (JsonNode question : read(mapper, file).get("questions")) {
                levels.add(new QuestionLevel(question.get("id").asText(), required(question, "level", file)));
            }
        }
        return levels;
    }

    /** The column defaults to MIDDLE, so a missing level here would be silently plausible. */
    private String required(JsonNode node, String field, String file) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            throw new IllegalStateException(
                    "Missing " + field + " in " + file + ": " + node.path("id").asText());
        }
        return value.asText().toUpperCase();
    }

    private JsonNode read(ObjectMapper mapper, String resource) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource: " + resource);
            }
            return mapper.readTree(in);
        }
    }

    private record SectionLevel(String topicId, String sectionId, String level) {
        @Override
        public String toString() {
            return topicId + "/" + sectionId;
        }
    }

    private record QuestionLevel(String id, String level) {
        @Override
        public String toString() {
            return id;
        }
    }
}
