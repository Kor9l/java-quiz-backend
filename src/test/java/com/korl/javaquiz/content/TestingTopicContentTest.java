package com.korl.javaquiz.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the Testing topic that {@code V24__LoadTestingTopic} loads. The migration runs once
 * against a real database, so a missing translation, an orphan question or a level nobody set
 * would surface as a failed deployment; these checks catch it at build time.
 */
class TestingTopicContentTest {

    private static final String TOPIC_ID = "testing";
    private static final int SECTION_COUNT = 8;
    private static final int QUESTIONS_PER_SECTION = 6;
    private static final int OPTIONS_PER_QUESTION = 5;
    private static final Set<String> DIFFICULTIES = Set.of("easy", "medium", "hard");
    private static final Set<String> LEVELS = Set.of("junior", "middle", "senior");
    private static final List<String> LEVEL_ORDER = List.of("junior", "middle", "senior");

    private static JsonNode topic;
    private static JsonNode materials;
    private static JsonNode questions;
    private static List<String> sectionIds;
    private static Map<String, String> sectionLevels;

    @BeforeAll
    static void load() throws Exception {
        topic = read("/content/" + TOPIC_ID + "/topic.json").get("topic");
        materials = read("/content/" + TOPIC_ID + "/materials.json");
        questions = read("/content/" + TOPIC_ID + "/questions.json");
        sectionIds = new ArrayList<>();
        sectionLevels = new LinkedHashMap<>();
        topic.get("sections").forEach(section -> {
            sectionIds.add(section.get("id").asText());
            sectionLevels.put(section.get("id").asText(), section.get("level").asText());
        });
    }

    private static JsonNode read(String path) throws Exception {
        try (InputStream in = TestingTopicContentTest.class.getResourceAsStream(path)) {
            assertThat(in).describedAs(path).isNotNull();
            return new ObjectMapper().readTree(in);
        }
    }

    private static List<JsonNode> list(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).toList();
    }

    private static List<JsonNode> sections() {
        return list(topic.get("sections"));
    }

    private static List<JsonNode> questionList() {
        return list(questions.get("questions"));
    }

    @Test
    void theTopicDeclaresEightUniqueSectionsInOrder() {
        assertThat(topic.get("id").asText()).isEqualTo(TOPIC_ID);
        // Sits after the concurrency and database topics in the catalogue.
        assertThat(topic.get("order").asInt()).isEqualTo(9);
        assertThat(sectionIds).doesNotHaveDuplicates().hasSize(SECTION_COUNT);

        int expected = 1;
        for (JsonNode section : sections()) {
            String id = section.get("id").asText();
            assertThat(section.get("order").asInt()).describedAs("order of %s", id).isEqualTo(expected++);
            for (String language : List.of("en", "ru")) {
                assertThat(section.get("title").get(language).asText())
                        .describedAs("%s title %s", id, language).isNotBlank();
            }
        }
    }

    /**
     * The column added by {@code V7__levels} defaults to {@code MIDDLE}, so a section that
     * forgets its level would load as middle instead of failing — hence the explicit check.
     */
    @Test
    void everySectionCarriesALevelAndAllThreeAreUsed() {
        List<String> levels = new ArrayList<>();
        for (JsonNode section : sections()) {
            String id = section.get("id").asText();
            assertThat(section.hasNonNull("level")).describedAs("level of %s is missing", id).isTrue();
            assertThat(LEVELS).describedAs("level of %s", id).contains(section.get("level").asText());
            levels.add(section.get("level").asText());
        }
        assertThat(levels).describedAs("a track with no sections at all is a dead track")
                .containsAll(LEVELS);
    }

    /**
     * Reading order runs from junior to senior, so a reader following the topic top to bottom is
     * never sent back to easier material after harder material.
     */
    @Test
    void sectionLevelsNeverGoBackwards() {
        int reached = 0;
        for (JsonNode section : sections()) {
            int level = LEVEL_ORDER.indexOf(section.get("level").asText());
            assertThat(level).describedAs("%s drops below the level reached before it",
                    section.get("id").asText()).isGreaterThanOrEqualTo(reached);
            reached = level;
        }
    }

    @Test
    void everyArticleIsWellFormed() {
        Set<String> seen = new HashSet<>();
        for (JsonNode section : list(materials.get("sections"))) {
            String id = section.get("id").asText();
            assertThat(seen.add(id)).describedAs("duplicate article for %s", id).isTrue();
            assertThat(sectionIds).describedAs("article for undeclared section %s", id).contains(id);
            assertThat(section.get("estimatedMinutes").asInt()).describedAs("minutes of %s", id)
                    .isBetween(11, 18);
            assertThat(section.get("sources")).describedAs("sources of %s", id).isNotEmpty();
            for (JsonNode source : section.get("sources")) {
                assertThat(source.get("title").asText()).describedAs("source title in %s", id).isNotBlank();
                assertThat(source.get("url").asText()).describedAs("source url in %s", id).isNotBlank();
            }
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
    void everyQuestionIsWellFormed() {
        Set<String> ids = new HashSet<>();
        for (JsonNode question : questionList()) {
            String id = question.get("id").asText();
            String section = question.get("section").asText();
            assertThat(ids.add(id)).describedAs("duplicate question id %s", id).isTrue();
            assertThat(sectionIds).describedAs("section of %s", id).contains(section);
            // Catches the copy-paste that leaves an id pointing at the section it came from.
            assertThat(id).describedAs("id of %s does not match its section", id)
                    .startsWith(TOPIC_ID + "." + section + ".");
            assertThat(DIFFICULTIES).describedAs("difficulty of %s", id)
                    .contains(question.get("difficulty").asText());
            assertThat(question.hasNonNull("level")).describedAs("level of %s is missing", id).isTrue();
            assertThat(LEVELS).describedAs("level of %s", id).contains(question.get("level").asText());
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
    }

    @Test
    void everySectionHasAnArticleAndAFullSetOfQuestions() {
        List<String> withArticle = list(materials.get("sections")).stream()
                .map(section -> section.get("id").asText())
                .toList();
        assertThat(withArticle).containsExactlyElementsOf(sectionIds);

        Map<String, Integer> perSection = new LinkedHashMap<>();
        sectionIds.forEach(id -> perSection.put(id, 0));
        questionList().forEach(question -> perSection.merge(question.get("section").asText(), 1, Integer::sum));
        assertThat(perSection).allSatisfy((section, count) ->
                assertThat(count).describedAs("questions in %s", section).isEqualTo(QUESTIONS_PER_SECTION));
    }

    /**
     * Every section mixes difficulty the same way — two easy, three medium, one hard — so that no
     * section is a wall for the track below it or filler for the track above it.
     */
    @Test
    void everySectionMixesDifficultyTheSameWay() {
        for (String section : sectionIds) {
            Map<String, Long> byDifficulty = new LinkedHashMap<>();
            DIFFICULTIES.forEach(difficulty -> byDifficulty.put(difficulty, 0L));
            questionList().stream()
                    .filter(question -> question.get("section").asText().equals(section))
                    .forEach(question -> byDifficulty.merge(question.get("difficulty").asText(), 1L, Long::sum));

            assertThat(byDifficulty.get("easy")).describedAs("easy questions in %s", section).isEqualTo(2);
            assertThat(byDifficulty.get("medium")).describedAs("medium questions in %s", section).isEqualTo(3);
            assertThat(byDifficulty.get("hard")).describedAs("hard questions in %s", section).isEqualTo(1);
        }
    }

    /**
     * A question's level follows from its section: easy sits one rung below the section, medium on
     * it, hard one rung above, clamped at the ends. Setting it by hand is how a section quietly
     * ends up feeding the wrong track.
     */
    @Test
    void questionLevelsFollowFromTheirSection() {
        for (JsonNode question : questionList()) {
            String id = question.get("id").asText();
            int sectionLevel = LEVEL_ORDER.indexOf(sectionLevels.get(question.get("section").asText()));
            int shift = switch (question.get("difficulty").asText()) {
                case "easy" -> -1;
                case "hard" -> 1;
                default -> 0;
            };
            int expected = Math.min(Math.max(sectionLevel + shift, 0), LEVEL_ORDER.size() - 1);
            assertThat(question.get("level").asText())
                    .describedAs("level of %s derived from its section and difficulty", id)
                    .isEqualTo(LEVEL_ORDER.get(expected));
        }
    }

    /**
     * A track whose pool is empty cannot be quizzed at all, and the level filter makes that
     * silent rather than loud — the session simply has nothing to ask.
     */
    @Test
    void everyTrackHasQuestionsOfItsOwnLevel() {
        for (String level : LEVELS) {
            assertThat(questionList().stream()
                    .filter(question -> question.get("level").asText().equals(level)))
                    .describedAs("questions at level %s", level)
                    .hasSizeGreaterThanOrEqualTo(12);
        }
    }

    /**
     * The correct option is spread across all five positions. Otherwise the bank silently
     * depends on the shuffle setting being switched on.
     */
    @Test
    void theCorrectAnswerIsNotAlwaysInTheSamePlace() {
        int[] positions = new int[OPTIONS_PER_QUESTION];
        for (JsonNode question : questionList()) {
            List<JsonNode> options = list(question.get("options"));
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).get("correct").asBoolean()) {
                    positions[i]++;
                }
            }
        }
        // Even to within one, rather than merely non-zero: a pile-up on one slot is guessable,
        // and "every slot is used" would not catch half the questions sitting in the middle.
        int even = questionList().size() / OPTIONS_PER_QUESTION;
        for (int position = 0; position < OPTIONS_PER_QUESTION; position++) {
            assertThat(positions[position]).describedAs("correct answers at position %d", position)
                    .isBetween(even, even + 1);
        }
    }
}
