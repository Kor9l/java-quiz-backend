package com.korl.javaquiz.practice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the bundled exercises. A task whose reference solution does not run, or which is
 * missing a translation, would only show up when a learner opened it — this catches it at
 * build time instead.
 */
class SqlPracticeContentTest {

    private static final Set<String> DIFFICULTIES = Set.of("easy", "medium", "hard");

    private static JsonNode root;
    private static Map<String, List<String>> datasets;

    private final SqlPracticeEngine engine = new SqlPracticeEngine(SandboxLimits.defaults());

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in = SqlPracticeContentTest.class.getResourceAsStream("/content/practice/sql.json")) {
            assertThat(in).describedAs("bundled SQL practice content").isNotNull();
            root = new ObjectMapper().readTree(in);
        }
        datasets = new HashMap<>();
        for (JsonNode dataset : root.get("datasets")) {
            List<String> setup = new ArrayList<>();
            dataset.get("setup").forEach(statement -> setup.add(statement.asText()));
            datasets.put(dataset.get("id").asText(), setup);
        }
    }

    private static List<JsonNode> tasks() {
        return StreamSupport.stream(root.get("tasks").spliterator(), false).toList();
    }

    private static TaskSpec spec(JsonNode task) {
        return new TaskSpec(
                task.get("id").asText(),
                datasets.get(task.get("dataset").asText()),
                task.get("solution").asText(),
                task.get("orderMatters").asBoolean());
    }

    @Test
    void everyTaskIsCompleteAndUniquelyIdentified() {
        Set<String> ids = new HashSet<>();
        for (JsonNode task : tasks()) {
            String id = task.get("id").asText();
            assertThat(ids.add(id)).describedAs("duplicate task id %s", id).isTrue();
            assertThat(datasets).describedAs("dataset of %s", id).containsKey(task.get("dataset").asText());
            assertThat(DIFFICULTIES).describedAs("difficulty of %s", id).contains(task.get("difficulty").asText());
            for (String field : List.of("title", "statement", "hint", "explanation")) {
                assertThat(task.get(field).get("en").asText()).describedAs("%s.%s.en", id, field).isNotBlank();
                assertThat(task.get(field).get("ru").asText()).describedAs("%s.%s.ru", id, field).isNotBlank();
            }
            assertThat(task.get("sources")).describedAs("sources of %s", id).isNotEmpty();
            assertThat(task.get("starter").asText()).describedAs("starter of %s", id).isNotBlank();
        }
    }

    /** A task pointing at a section that does not exist would render a dead link. */
    @Test
    void everyTaskPointsAtARealStudySection() throws Exception {
        JsonNode topic;
        try (InputStream in = SqlPracticeContentTest.class.getResourceAsStream("/content/sql/topic.json")) {
            assertThat(in).describedAs("bundled SQL topic definition").isNotNull();
            topic = new ObjectMapper().readTree(in).get("topic");
        }
        Set<String> sectionIds = new HashSet<>();
        topic.get("sections").forEach(section -> sectionIds.add(section.get("id").asText()));

        for (JsonNode task : tasks()) {
            String id = task.get("id").asText();
            assertThat(task.path("topic").asText()).describedAs("topic of %s", id)
                    .isEqualTo(topic.get("id").asText());
            assertThat(sectionIds).describedAs("section of %s", id)
                    .contains(task.path("section").asText());
        }
    }

    @Test
    void everyDifficultyHasTasks() {
        for (String difficulty : DIFFICULTIES) {
            assertThat(tasks().stream().filter(t -> t.get("difficulty").asText().equals(difficulty)))
                    .describedAs("tasks of difficulty %s", difficulty)
                    .hasSizeGreaterThanOrEqualTo(5);
        }
    }

    /** Every reference solution has to run and return something to compare against. */
    @TestFactory
    List<DynamicTest> referenceSolutionsRun() {
        return tasks().stream()
                .map(task -> DynamicTest.dynamicTest(task.get("id").asText(), () -> {
                    TaskSpec spec = spec(task);
                    ResultTable expected = engine.expectedResult(spec);
                    assertThat(expected.rowCount()).describedAs("rows produced").isPositive();
                    assertThat(expected.columnCount()).describedAs("columns produced").isPositive();
                    assertThat(expected.truncated()).describedAs("result fits the row limit").isFalse();
                }))
                .toList();
    }

    /** The reference solution must of course grade itself as correct. */
    @TestFactory
    List<DynamicTest> referenceSolutionsGradeAsCorrect() {
        return tasks().stream()
                .map(task -> DynamicTest.dynamicTest(task.get("id").asText(), () -> {
                    TaskSpec spec = spec(task);
                    assertThat(engine.grade(spec, spec.solutionSql()).status())
                            .isEqualTo(SubmissionStatus.PASSED);
                }))
                .toList();
    }

    @Test
    void starterCodeIsAStartingPointRatherThanAnAnswer() {
        for (JsonNode task : tasks()) {
            String starter = task.get("starter").asText();
            assertThat(starter.trim())
                    .describedAs("starter of %s must not be the solution", task.get("id").asText())
                    .isNotEqualToIgnoringWhitespace(task.get("solution").asText().trim());
        }
    }
}
