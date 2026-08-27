package com.korl.javaquiz.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the SQL topic that {@code V6__LoadSqlTopic} loads. The migration runs once against a
 * real database, so a missing translation or a question pointing at a section that does not
 * exist would surface as a failed deployment; these checks catch it at build time.
 */
class SqlTopicContentTest {

    private static final Set<String> DIFFICULTIES = Set.of("easy", "medium", "hard");
    private static final int OPTIONS_PER_QUESTION = 5;

    private static JsonNode topic;
    private static JsonNode materials;
    private static JsonNode questions;
    private static List<String> sectionIds;

    @BeforeAll
    static void load() throws Exception {
        topic = read("/content/sql/topic.json").get("topic");
        materials = read("/content/sql/materials.json");
        questions = read("/content/sql/questions.json");
        sectionIds = new ArrayList<>();
        topic.get("sections").forEach(section -> sectionIds.add(section.get("id").asText()));
    }

    private static JsonNode read(String path) throws Exception {
        try (InputStream in = SqlTopicContentTest.class.getResourceAsStream(path)) {
            assertThat(in).describedAs(path).isNotNull();
            return new ObjectMapper().readTree(in);
        }
    }

    private static List<JsonNode> list(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).toList();
    }

    @Test
    void theTopicDeclaresUniqueSectionsInOrder() {
        assertThat(topic.get("id").asText()).isEqualTo("sql");
        assertThat(sectionIds).doesNotHaveDuplicates().isNotEmpty();
        int expected = 1;
        for (JsonNode section : topic.get("sections")) {
            assertThat(section.get("order").asInt()).describedAs("order of %s", section.get("id").asText())
                    .isEqualTo(expected++);
            for (String language : List.of("en", "ru")) {
                assertThat(section.get("title").get(language).asText()).isNotBlank();
            }
        }
    }

    @Test
    void everySectionHasAnArticleInBothLanguages() {
        List<String> withMaterial = list(materials.get("sections")).stream()
                .map(section -> section.get("id").asText())
                .toList();
        assertThat(withMaterial).containsExactlyElementsOf(sectionIds);

        for (JsonNode section : materials.get("sections")) {
            String id = section.get("id").asText();
            assertThat(section.get("estimatedMinutes").asInt()).describedAs("minutes of %s", id).isPositive();
            assertThat(section.get("sources")).describedAs("sources of %s", id).isNotEmpty();
            for (String language : List.of("en", "ru")) {
                assertThat(section.get("summary").get(language).asText())
                        .describedAs("%s summary %s", id, language).isNotBlank();
                // Short enough to be a stub rather than an article is the failure worth catching.
                assertThat(section.get("body").get(language).asText().length())
                        .describedAs("%s body %s", id, language).isGreaterThan(1500);
            }
        }
    }

    @Test
    void everySectionHasQuestionsAndEveryQuestionIsWellFormed() {
        Set<String> ids = new HashSet<>();
        for (JsonNode question : questions.get("questions")) {
            String id = question.get("id").asText();
            assertThat(ids.add(id)).describedAs("duplicate question id %s", id).isTrue();
            assertThat(sectionIds).describedAs("section of %s", id).contains(question.get("section").asText());
            assertThat(DIFFICULTIES).describedAs("difficulty of %s", id).contains(question.get("difficulty").asText());
            assertThat(question.get("sources")).describedAs("sources of %s", id).isNotEmpty();

            List<JsonNode> options = list(question.get("options"));
            assertThat(options).describedAs("options of %s", id).hasSize(OPTIONS_PER_QUESTION);
            assertThat(options.stream().filter(option -> option.get("correct").asBoolean()).count())
                    .describedAs("exactly one correct option in %s", id).isEqualTo(1);

            for (String language : List.of("en", "ru")) {
                assertThat(question.get("text").get(language).asText())
                        .describedAs("%s text %s", id, language).isNotBlank();
                assertThat(question.get("explanation").get(language).asText())
                        .describedAs("%s explanation %s", id, language).isNotBlank();
                for (JsonNode option : options) {
                    assertThat(option.get("text").get(language).asText())
                            .describedAs("%s option %s", id, language).isNotBlank();
                }
            }
        }

        for (String sectionId : sectionIds) {
            assertThat(list(questions.get("questions")).stream()
                    .filter(question -> question.get("section").asText().equals(sectionId)))
                    .describedAs("questions in section %s", sectionId)
                    .hasSizeGreaterThanOrEqualTo(5);
        }
    }

    /**
     * The correct option is spread across all five positions. Otherwise the bank silently
     * depends on the shuffle setting being switched on.
     */
    @Test
    void theCorrectAnswerIsNotAlwaysInTheSamePlace() {
        int[] positions = new int[OPTIONS_PER_QUESTION];
        for (JsonNode question : questions.get("questions")) {
            List<JsonNode> options = list(question.get("options"));
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).get("correct").asBoolean()) {
                    positions[i]++;
                }
            }
        }
        for (int position = 0; position < OPTIONS_PER_QUESTION; position++) {
            assertThat(positions[position]).describedAs("correct answers at position %d", position).isPositive();
        }
    }
}
