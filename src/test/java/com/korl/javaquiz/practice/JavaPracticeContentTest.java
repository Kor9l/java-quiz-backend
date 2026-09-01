package com.korl.javaquiz.practice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the bundled Java exercises. A task whose reference solution no longer compiles, or
 * whose cases no longer call methods the solution has, would only show up when a learner opened
 * it — and the migration that loads it only gets to run once, against a real database. This
 * catches both at build time instead.
 */
class JavaPracticeContentTest {

    private static final Set<String> DIFFICULTIES = Set.of("easy", "medium", "hard");

    private static JsonNode root;

    private final JavaPracticeEngine engine = new JavaPracticeEngine(JavaLimits.defaults());

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in = JavaPracticeContentTest.class.getResourceAsStream("/content/practice/java.json")) {
            assertThat(in).describedAs("bundled Java practice content").isNotNull();
            root = new ObjectMapper().readTree(in);
        }
    }

    private static List<JsonNode> tasks() {
        return StreamSupport.stream(root.get("tasks").spliterator(), false).toList();
    }

    private static JavaTaskSpec spec(JsonNode task) {
        List<JavaTaskSpec.Case> cases = new ArrayList<>();
        for (JsonNode current : task.get("cases")) {
            cases.add(new JavaTaskSpec.Case(current.get("label").asText(), current.get("expression").asText()));
        }
        return new JavaTaskSpec(
                task.get("id").asText(),
                task.get("className").asText(),
                task.get("solution").asText(),
                List.copyOf(cases));
    }

    @Test
    void everyTaskIsCompleteAndUniquelyIdentified() {
        Set<String> ids = new HashSet<>();
        for (JsonNode task : tasks()) {
            String id = task.get("id").asText();
            assertThat(ids.add(id)).describedAs("duplicate task id %s", id).isTrue();
            assertThat(DIFFICULTIES).describedAs("difficulty of %s", id).contains(task.get("difficulty").asText());
            for (String field : List.of("title", "statement", "hint", "explanation")) {
                assertThat(task.get(field).get("en").asText()).describedAs("%s.%s.en", id, field).isNotBlank();
                assertThat(task.get(field).get("ru").asText()).describedAs("%s.%s.ru", id, field).isNotBlank();
            }
            assertThat(task.get("sources")).describedAs("sources of %s", id).isNotEmpty();
            assertThat(task.get("starter").asText()).describedAs("starter of %s", id).isNotBlank();
            assertThat(task.get("className").asText()).describedAs("className of %s", id).isNotBlank();
            assertThat(task.get("cases")).describedAs("cases of %s", id).isNotEmpty();
        }
    }

    /** A task pointing at a section that does not exist would render a dead link. */
    @Test
    void everyTaskPointsAtARealStudySection() throws Exception {
        JsonNode topics;
        try (InputStream in = JavaPracticeContentTest.class.getResourceAsStream("/content/topics.json")) {
            assertThat(in).describedAs("bundled topic definitions").isNotNull();
            topics = new ObjectMapper().readTree(in).get("topics");
        }
        for (JsonNode task : tasks()) {
            String id = task.get("id").asText();
            String topicId = task.path("topic").asText();
            JsonNode topic = StreamSupport.stream(topics.spliterator(), false)
                    .filter(candidate -> candidate.get("id").asText().equals(topicId))
                    .findFirst()
                    .orElse(null);
            assertThat(topic).describedAs("topic %s of %s", topicId, id).isNotNull();

            Set<String> sectionIds = new HashSet<>();
            topic.get("sections").forEach(section -> sectionIds.add(section.get("id").asText()));
            assertThat(sectionIds).describedAs("section of %s", id).contains(task.path("section").asText());
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

    /**
     * Every reference solution has to compile, run and return something for each case. This is
     * the test that would have caught a case calling a method the solution does not have.
     */
    @TestFactory
    List<DynamicTest> referenceSolutionsRun() {
        return tasks().stream()
                .map(task -> DynamicTest.dynamicTest(task.get("id").asText(), () -> {
                    JavaTaskSpec spec = spec(task);
                    ResultTable expected = engine.expectedResult(spec);
                    assertThat(expected.rowCount())
                            .describedAs("one row per case").isEqualTo(spec.cases().size());
                    assertThat(expected.columnCount()).describedAs("case and result").isEqualTo(2);
                }))
                .toList();
    }

    /** The reference solution must of course grade itself as correct. */
    @TestFactory
    List<DynamicTest> referenceSolutionsGradeAsCorrect() {
        return tasks().stream()
                .map(task -> DynamicTest.dynamicTest(task.get("id").asText(), () -> {
                    JavaTaskSpec spec = spec(task);
                    assertThat(engine.grade(spec, spec.solutionCode()).status())
                            .isEqualTo(SubmissionStatus.PASSED);
                }))
                .toList();
    }

    /**
     * The starter must never already be the answer.
     *
     * <p>It is allowed not to compile: an exercise whose point <em>is</em> the signature — the
     * wildcard one, say — starts from a signature too narrow for its own cases, and that is the
     * lesson rather than a mistake. What it may not do is pass.
     */
    @TestFactory
    List<DynamicTest> starterCodeIsAStartingPointRatherThanAnAnswer() {
        return tasks().stream()
                .map(task -> DynamicTest.dynamicTest(task.get("id").asText(), () -> {
                    JavaTaskSpec spec = spec(task);
                    String starter = task.get("starter").asText();
                    assertThat(starter.trim())
                            .describedAs("starter must not be the solution")
                            .isNotEqualToIgnoringWhitespace(spec.solutionCode().trim());
                    SubmissionStatus status;
                    try {
                        status = engine.grade(spec, starter).status();
                    } catch (PracticeSubmissionException refused) {
                        status = refused.getStatus();
                    }
                    assertThat(status).describedAs("starter must not pass").isNotEqualTo(SubmissionStatus.PASSED);
                }))
                .toList();
    }

    /** Nothing a task ships may be something the sandbox would then refuse to run. */
    @TestFactory
    List<DynamicTest> everyStarterAndSolutionSatisfiesTheSandboxPolicy() {
        return tasks().stream()
                .map(task -> DynamicTest.dynamicTest(task.get("id").asText(), () -> {
                    JavaTaskSpec spec = spec(task);
                    assertThat(engine.checkCompilation(spec, spec.solutionCode()).status())
                            .describedAs("solution compiles and is within the policy")
                            .isEqualTo(SubmissionStatus.PASSED);
                }))
                .toList();
    }
}
