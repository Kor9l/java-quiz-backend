package com.korl.javaquiz.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Holds the level ladder up for the six topics that were written before it existed.
 *
 * <p>They were loaded by {@code V2} and {@code V6} and tagged {@code MIDDLE} wholesale by
 * {@code V7__levels}'s column default, which left the junior track drawing on one topic out of
 * seven and the senior track on twenty questions. {@code V22__BackfillLevels} reads the levels
 * out of these content files, so a level missing here is a question that stays middle for good —
 * silently, because the column is never null and a wrong level looks exactly like a right one.
 *
 * <p>This is the test the other content tests have for their own topics, kept apart because
 * these six share two files rather than shipping a directory each.
 */
class BackendLevelsContentTest {

    private static final List<String> LADDER = List.of("junior", "middle", "senior");

    /** One full round at every level, so no track lands on a pool it exhausts immediately. */
    private static final int MINIMUM_PER_LEVEL = 6;

    private static final Map<String, String> QUESTION_FILES = new LinkedHashMap<>(Map.of(
            "java-core", "/content/questions/java-core.json",
            "spring", "/content/questions/spring.json",
            "spring-boot", "/content/questions/spring-boot.json",
            "hibernate", "/content/questions/hibernate.json",
            "kafka", "/content/questions/kafka.json",
            "sql", "/content/sql/questions.json"));

    /** Section level by topic and section id, from both files that define these topics. */
    private static Map<String, Map<String, String>> sectionLevels;

    private static Map<String, JsonNode> questionsByTopic;

    @BeforeAll
    static void load() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        sectionLevels = new HashMap<>();
        JsonNode shared = read(mapper, "/content/topics.json");
        for (JsonNode topic : shared.get("topics")) {
            collect(topic);
        }
        collect(read(mapper, "/content/sql/topic.json").get("topic"));

        questionsByTopic = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : QUESTION_FILES.entrySet()) {
            questionsByTopic.put(entry.getKey(), read(mapper, entry.getValue()));
        }
    }

    private static void collect(JsonNode topic) {
        Map<String, String> levels = new HashMap<>();
        topic.get("sections").forEach(section -> levels.put(
                section.get("id").asText(), section.path("level").asText(null)));
        sectionLevels.put(topic.get("id").asText(), levels);
    }

    private static JsonNode read(ObjectMapper mapper, String resource) throws Exception {
        try (InputStream in = BackendLevelsContentTest.class.getResourceAsStream(resource)) {
            assertThat(in).describedAs("bundled content %s", resource).isNotNull();
            return mapper.readTree(in);
        }
    }

    @Test
    void everySectionSaysWhoItIsFor() {
        assertThat(sectionLevels.keySet()).containsAll(QUESTION_FILES.keySet());
        sectionLevels.forEach((topicId, sections) -> sections.forEach((sectionId, level) ->
                assertThat(level).describedAs("level of %s/%s", topicId, sectionId).isIn(LADDER)));
    }

    @TestFactory
    List<DynamicTest> everyQuestionSaysWhoItIsFor() {
        List<DynamicTest> tests = new ArrayList<>();
        questionsByTopic.forEach((topicId, root) -> tests.add(DynamicTest.dynamicTest(topicId, () -> {
            for (JsonNode question : root.get("questions")) {
                String id = question.get("id").asText();
                assertThat(question.path("level").asText(null)).describedAs("level of %s", id).isIn(LADDER);
            }
        })));
        return tests;
    }

    /**
     * A question may sit a rung away from its article but no further: easy material inside a
     * senior section is still middle, and a hard junior question is still a middle one. Two rungs
     * would mean the section and the question disagree about what the topic is, and the section
     * is what the reader is sent to when they get the question wrong.
     */
    @TestFactory
    List<DynamicTest> noQuestionIsMoreThanOneRungFromItsSection() {
        List<DynamicTest> tests = new ArrayList<>();
        questionsByTopic.forEach((topicId, root) -> tests.add(DynamicTest.dynamicTest(topicId, () -> {
            Map<String, String> sections = sectionLevels.get(topicId);
            for (JsonNode question : root.get("questions")) {
                String id = question.get("id").asText();
                String sectionId = question.get("section").asText();
                assertThat(sections).describedAs("section %s of %s", sectionId, id).containsKey(sectionId);
                int distance = Math.abs(
                        LADDER.indexOf(question.get("level").asText()) - LADDER.indexOf(sections.get(sectionId)));
                assertThat(distance)
                        .describedAs("%s is %s, its section is %s", id,
                                question.get("level").asText(), sections.get(sectionId))
                        .isLessThanOrEqualTo(1);
            }
        })));
        return tests;
    }

    /**
     * The failure this whole stage exists to prevent: a track whose pool is empty or nearly so.
     * A junior draws on junior alone — nothing sits below it — so a topic with no junior
     * questions is a topic that answers a junior with an empty round.
     */
    @TestFactory
    List<DynamicTest> everyTopicCanFillARoundAtEveryLevel() {
        List<DynamicTest> tests = new ArrayList<>();
        questionsByTopic.forEach((topicId, root) -> tests.add(DynamicTest.dynamicTest(topicId, () -> {
            Map<String, Integer> counted = new HashMap<>();
            root.get("questions").forEach(question ->
                    counted.merge(question.get("level").asText(), 1, Integer::sum));
            for (String level : LADDER) {
                assertThat(counted.getOrDefault(level, 0))
                        .describedAs("%s questions in %s", level, topicId)
                        .isGreaterThanOrEqualTo(MINIMUM_PER_LEVEL);
            }
        })));
        return tests;
    }
}
