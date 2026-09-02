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
 * Guards the base grammar course that {@code V16__LoadGrammarBase} loads. The migration runs once
 * against a real database, so a missing translation, an orphan question or a level nobody set
 * would surface as a failed deployment; these checks catch it at build time.
 *
 * <p>Two checks differ from the backend topics' tests, and both follow from grammar being graded
 * on its own ladder. A backend topic spans junior to senior and is checked for using all three;
 * a grammar course runs one level end to end, so this one is checked for using <em>only</em>
 * {@code base}. And a level from the backend ladder here would not merely be wrong — it would put
 * the row in a pool no English track ever queries, so the round would come out empty rather than
 * odd.
 */
class GrammarBaseContentTest {

    private static final String TOPIC_ID = "grammar-base";
    private static final String BASE = "/content/english/grammar/base/";
    private static final int SECTION_COUNT = 14;
    private static final int QUESTIONS_PER_SECTION = 6;
    private static final int OPTIONS_PER_QUESTION = 5;
    private static final Set<String> DIFFICULTIES = Set.of("easy", "medium", "hard");
    private static final String LEVEL = "base";
    private static final Set<String> AREAS = Set.of(
            "syntax", "verbs", "nouns", "articles", "pronouns", "tenses",
            "modals", "adjectives", "prepositions", "quantifiers");

    private static JsonNode topic;
    private static JsonNode materials;
    private static JsonNode questions;
    private static List<String> sectionIds;

    @BeforeAll
    static void load() throws Exception {
        topic = read(BASE + "topic.json").get("topic");
        materials = read(BASE + "materials.json");
        questions = read(BASE + "questions.json");
        sectionIds = new ArrayList<>();
        topic.get("sections").forEach(section -> sectionIds.add(section.get("id").asText()));
    }

    private static JsonNode read(String path) throws Exception {
        try (InputStream in = GrammarBaseContentTest.class.getResourceAsStream(path)) {
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

    /**
     * The module is what keeps the course out of the backend's topic list, its "all topics" quiz
     * pool and its stats breakdown. The column defaults to backend, so a course that forgets to
     * declare itself would load as backend material and leak into all three.
     */
    @Test
    void theCourseDeclaresItselfAsEnglishMaterial() {
        assertThat(topic.get("id").asText()).isEqualTo(TOPIC_ID);
        assertThat(topic.hasNonNull("module")).describedAs("module is missing").isTrue();
        assertThat(topic.get("module").asText()).isEqualTo("english");
        // Ordering is within a module, so the grammar courses number from one of their own.
        assertThat(topic.get("order").asInt()).isEqualTo(1);
    }

    @Test
    void theCourseDeclaresFourteenUniqueSectionsInOrder() {
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
     * A course is one level end to end, which is what makes it readable as a course. The
     * cumulative track still works, because a pro round draws on all three grammar courses at
     * once rather than on three levels inside one of them.
     */
    @Test
    void everySectionIsBaseLevelAndCarriesAnArea() {
        for (JsonNode section : sections()) {
            String id = section.get("id").asText();
            assertThat(section.hasNonNull("level")).describedAs("level of %s is missing", id).isTrue();
            assertThat(section.get("level").asText()).describedAs("level of %s", id).isEqualTo(LEVEL);
            assertThat(section.hasNonNull("area")).describedAs("area of %s is missing", id).isTrue();
            assertThat(AREAS).describedAs("area of %s", id).contains(section.get("area").asText());
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
                    .isBetween(8, 30);
            assertThat(section.get("sources")).describedAs("sources of %s", id).isNotEmpty();
            for (JsonNode source : section.get("sources")) {
                assertThat(source.get("title").asText()).describedAs("source title in %s", id).isNotBlank();
                assertThat(source.get("url").asText()).describedAs("source url in %s", id).startsWith("https://");
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
            assertThat(question.get("level").asText()).describedAs("level of %s", id).isEqualTo(LEVEL);
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
                List<String> texts = new ArrayList<>();
                for (JsonNode option : options) {
                    String text = option.get("text").get(language).asText();
                    assertThat(text).describedAs("%s option %s", id, language).isNotBlank();
                    texts.add(text);
                }
                // Two identical options mean the question has two right answers or two wrong
                // ones wearing the same clothes; either way it cannot be answered as asked.
                assertThat(texts).describedAs("%s repeats an option in %s", id, language)
                        .doesNotHaveDuplicates();
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

    /** The mix the rest of the bank uses, so a grammar round feels like the others. */
    @Test
    void difficultyIsMixedRatherThanFlat() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        DIFFICULTIES.forEach(difficulty -> counts.put(difficulty, 0));
        questionList().forEach(question -> counts.merge(question.get("difficulty").asText(), 1, Integer::sum));

        int total = questionList().size();
        assertThat(total).isEqualTo(SECTION_COUNT * QUESTIONS_PER_SECTION);
        assertThat(counts.get("easy")).describedAs("easy share").isBetween(total / 4, total / 2);
        assertThat(counts.get("medium")).describedAs("medium share").isBetween(total / 3, total * 2 / 3);
        assertThat(counts.get("hard")).describedAs("hard share").isBetween(total / 10, total / 4);
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
        for (int position = 0; position < OPTIONS_PER_QUESTION; position++) {
            assertThat(positions[position]).describedAs("correct answers at position %d", position).isPositive();
        }
    }
}
