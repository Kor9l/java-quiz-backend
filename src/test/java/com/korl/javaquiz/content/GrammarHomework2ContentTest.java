package com.korl.javaquiz.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.korl.javaquiz.practice.GrammarAnswerMatcher;
import com.korl.javaquiz.practice.GrammarExerciseKind;
import com.korl.javaquiz.practice.GrammarLimits;
import com.korl.javaquiz.practice.GrammarPracticeEngine;
import com.korl.javaquiz.practice.GrammarTaskSpec;
import com.korl.javaquiz.practice.SubmissionOutcome;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the second homework handout, which {@code V43__LoadGrammarHomework2} adds to the course
 * {@code V20__LoadGrammarHomework} created. Same reasoning as {@link GrammarHomeworkContentTest}:
 * a migration gets one attempt against a live database, so the content is held here.
 *
 * <p>Two things are new. The handout follows on from the first one inside the same course and
 * the same practice track, so section orders, exercise ids and the per-difficulty numbering are
 * checked across both files rather than within this one. And its exercises are {@code GAP_FILL}:
 * every gap marked in a text must have answers and every answer must have a gap — which is
 * checked by running the real engine over the bundled answers, gap by gap.
 */
class GrammarHomework2ContentTest {

    private static final String TOPIC_ID = "grammar-homework";
    private static final String FIRST = "/content/english/grammar/homework/";
    private static final String BASE = "/content/english/grammar/homework-2/";
    private static final String HANDOUT_PREFIX = "hw-";
    private static final int QUESTIONS_PER_THEORY_SECTION = 6;
    private static final int OPTIONS_PER_QUESTION = 5;
    private static final List<String> DIFFICULTIES = List.of("easy", "medium", "hard");
    private static final Set<String> ENGLISH_LEVELS = Set.of("base", "intermediate", "pro");
    private static final Set<String> AREAS = Set.of(
            "syntax", "verbs", "nouns", "articles", "pronouns", "tenses",
            "modals", "adjectives", "prepositions", "quantifiers");

    private static JsonNode firstTopic;
    private static JsonNode firstExercises;
    private static JsonNode sections;
    private static JsonNode materials;
    private static JsonNode questions;
    private static JsonNode exercises;

    private final GrammarPracticeEngine engine = new GrammarPracticeEngine(GrammarLimits.defaults());

    @BeforeAll
    static void load() throws Exception {
        firstTopic = read(FIRST + "topic.json").get("topic");
        firstExercises = read(FIRST + "exercises.json");
        sections = read(BASE + "sections.json");
        materials = read(BASE + "materials.json");
        questions = read(BASE + "questions.json");
        exercises = read(BASE + "exercises.json");
    }

    private static JsonNode read(String path) throws Exception {
        try (InputStream in = GrammarHomework2ContentTest.class.getResourceAsStream(path)) {
            assertThat(in).describedAs(path).isNotNull();
            return new ObjectMapper().readTree(in);
        }
    }

    private static List<JsonNode> list(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).toList();
    }

    private static List<JsonNode> sectionList() {
        return list(sections.get("sections"));
    }

    private static List<JsonNode> taskList() {
        return list(exercises.get("tasks"));
    }

    private static boolean isHandout(String sectionId) {
        return sectionId.startsWith(HANDOUT_PREFIX);
    }

    private static void assertBilingual(JsonNode node, String field, String described) {
        for (String language : List.of("en", "ru")) {
            assertThat(node.path(field).path(language).asText(""))
                    .describedAs("%s %s (%s)", described, field, language).isNotBlank();
        }
    }

    /** The sections join an existing course, so they continue its order and never reuse an id. */
    @Test
    void theSectionsContinueTheExistingCourse() {
        assertThat(sections.get("topic").asText()).isEqualTo(TOPIC_ID);
        Set<String> taken = new HashSet<>();
        int lastOrder = 0;
        for (JsonNode section : list(firstTopic.get("sections"))) {
            taken.add(section.get("id").asText());
            lastOrder = Math.max(lastOrder, section.get("order").asInt());
        }
        for (JsonNode section : sectionList()) {
            String id = section.get("id").asText();
            assertThat(taken.add(id)).describedAs("section id %s is already used", id).isTrue();
            assertThat(section.get("order").asInt()).describedAs("order of %s", id).isEqualTo(++lastOrder);
            assertThat(ENGLISH_LEVELS).describedAs("level of %s", id).contains(section.path("level").asText());
            assertThat(AREAS).describedAs("area of %s", id).contains(section.path("area").asText());
            assertBilingual(section, "title", id);
        }
    }

    /** A theory section is followed by the handout that drills it, so the course reads in pairs. */
    @Test
    void everyTheorySectionIsFollowedByItsHandout() {
        List<JsonNode> list = sectionList();
        assertThat(list.size() % 2).describedAs("sections come in pairs").isZero();
        for (int i = 0; i < list.size(); i += 2) {
            String theory = list.get(i).get("id").asText();
            String handout = list.get(i + 1).get("id").asText();
            assertThat(isHandout(theory)).describedAs("%s should be theory", theory).isFalse();
            assertThat(handout).isEqualTo(HANDOUT_PREFIX + theory);
        }
    }

    @Test
    void everySectionHasAnArticleInBothLanguages() {
        List<String> ids = sectionList().stream().map(section -> section.get("id").asText()).toList();
        List<JsonNode> articles = list(materials.get("sections"));
        assertThat(articles.stream().map(article -> article.get("id").asText()))
                .containsExactlyInAnyOrderElementsOf(ids);
        for (JsonNode article : articles) {
            String id = article.get("id").asText();
            assertBilingual(article, "summary", id);
            assertBilingual(article, "body", id);
            assertThat(article.get("estimatedMinutes").asInt()).describedAs("minutes of %s", id).isBetween(11, 18);
            assertThat(list(article.get("sources"))).describedAs("sources of %s", id).isNotEmpty();
            for (JsonNode source : article.get("sources")) {
                assertThat(source.get("url").asText()).describedAs("source of %s", id).startsWith("https://");
            }
        }
    }

    @Test
    void everyTheorySectionCarriesAWellFormedQuizAndNoHandoutDoes() {
        Map<String, Integer> perSection = new LinkedHashMap<>();
        List<String> ids = new ArrayList<>();
        for (JsonNode question : list(questions.get("questions"))) {
            String id = question.get("id").asText();
            String section = question.get("section").asText();
            ids.add(id);
            perSection.merge(section, 1, Integer::sum);
            assertThat(id).describedAs("id of %s", id).startsWith(TOPIC_ID + "." + section + ".");
            assertThat(DIFFICULTIES).describedAs("difficulty of %s", id).contains(question.get("difficulty").asText());
            assertThat(ENGLISH_LEVELS).describedAs("level of %s", id).contains(question.path("level").asText());
            assertBilingual(question, "text", id);
            assertBilingual(question, "explanation", id);
            assertThat(list(question.get("sources"))).describedAs("sources of %s", id).isNotEmpty();

            List<JsonNode> options = list(question.get("options"));
            assertThat(options).describedAs("options of %s", id).hasSize(OPTIONS_PER_QUESTION);
            assertThat(options.stream().filter(option -> option.get("correct").asBoolean()).count())
                    .describedAs("correct options of %s", id).isEqualTo(1);
            assertThat(options.stream().map(option -> option.get("text").get("en").asText()))
                    .describedAs("options of %s repeat", id).doesNotHaveDuplicates();
            options.forEach(option -> assertBilingual(option, "text", id));
        }
        assertThat(ids).doesNotHaveDuplicates();
        List<String> theory = sectionList().stream()
                .map(section -> section.get("id").asText())
                .filter(id -> !isHandout(id))
                .toList();
        assertThat(perSection.keySet()).containsExactlyInAnyOrderElementsOf(theory);
        perSection.forEach((section, count) ->
                assertThat(count).describedAs("questions in %s", section).isEqualTo(QUESTIONS_PER_THEORY_SECTION));
    }

    @Test
    void theExercisesAreGapFillsAttachedToAHandoutSection() {
        assertThat(exercises.get("track").asText()).isEqualTo("grammar");
        Set<String> handouts = new HashSet<>();
        sectionList().forEach(section -> handouts.add(section.get("id").asText()));
        handouts.removeIf(id -> !isHandout(id));

        Set<String> used = new HashSet<>();
        for (JsonNode task : taskList()) {
            String id = task.get("id").asText();
            assertThat(task.get("topic").asText()).describedAs("topic of %s", id).isEqualTo(TOPIC_ID);
            assertThat(handouts).describedAs("section of %s", id).contains(task.get("section").asText());
            assertThat(task.get("kind").asText().toUpperCase(Locale.ROOT)).describedAs("kind of %s", id)
                    .isEqualTo(GrammarExerciseKind.GAP_FILL.name());
            used.add(task.get("section").asText());
            assertBilingual(task, "title", id);
            assertBilingual(task, "statement", id);
            assertBilingual(task, "hint", id);
            assertBilingual(task, "explanation", id);
            assertThat(list(task.get("sources"))).describedAs("sources of %s", id).isNotEmpty();
        }
        assertThat(used).describedAs("every handout has exercises").containsExactlyInAnyOrderElementsOf(handouts);
    }

    /**
     * The track is one list per difficulty across both handouts, so the numbering carries on
     * from the first file: ids stay unique and orders run 1..n with no gap and no repeat.
     */
    @Test
    void theNumberingCarriesOnFromTheFirstHandout() {
        Map<String, List<Integer>> orders = new LinkedHashMap<>();
        List<String> ids = new ArrayList<>();
        for (JsonNode task : list(firstExercises.get("tasks"))) {
            ids.add(task.get("id").asText());
            orders.computeIfAbsent(task.get("difficulty").asText(), key -> new ArrayList<>()).add(task.get("order").asInt());
        }
        for (JsonNode task : taskList()) {
            String id = task.get("id").asText();
            String difficulty = task.get("difficulty").asText();
            assertThat(DIFFICULTIES).describedAs("difficulty of %s", id).contains(difficulty);
            int order = task.get("order").asInt();
            assertThat(id).isEqualTo(String.format("grammar-%s-%02d", difficulty, order));
            ids.add(id);
            orders.computeIfAbsent(difficulty, key -> new ArrayList<>()).add(order);
        }
        assertThat(ids).doesNotHaveDuplicates();
        orders.forEach((difficulty, values) -> assertThat(values.stream().sorted().toList())
                .describedAs("orders of %s", difficulty)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, values.size()).boxed().toList()));
    }

    /**
     * The checks a proofreader misses: a gap typed as {@code {{4}}} twice, an answer list one
     * short, or an accepted form the matcher would not actually accept. Each accepted answer is
     * put into its own gap with the reference answers in every other gap, and the whole text must
     * pass — and an empty answer must fail every gap, so a gap that accepts anything is caught too.
     */
    @Test
    void everyGapHasAnswersAndEveryAcceptedAnswerPassesTheEngine() {
        for (JsonNode task : taskList()) {
            String id = task.get("id").asText();
            String sentence = task.get("sentence").asText();
            List<Integer> gaps = GrammarAnswerMatcher.gaps(sentence);
            assertThat(gaps).describedAs("gaps of %s", id)
                    .isNotEmpty()
                    .containsExactlyElementsOf(IntStream.rangeClosed(1, gaps.size()).boxed().toList());
            // Every gap gives the word to put into form, the way the handout prints it.
            List<String> afterGaps = List.of(sentence.split("\\{\\{\\d+}}", -1));
            assertThat(afterGaps.subList(1, afterGaps.size())).describedAs("brackets after gaps of %s", id)
                    .allSatisfy(after -> assertThat(after).startsWith(" ("));

            List<GrammarTaskSpec.Blank> blanks = new ArrayList<>();
            for (JsonNode blank : task.get("blanks")) {
                List<String> accepted = list(blank.get("accepted")).stream().map(JsonNode::asText).toList();
                assertThat(accepted).describedAs("answers for gap %s of %s", blank.get("index"), id)
                        .isNotEmpty().doesNotHaveDuplicates();
                blanks.add(new GrammarTaskSpec.Blank(blank.get("index").asInt(), accepted));
            }
            assertThat(blanks.stream().map(GrammarTaskSpec.Blank::index).toList())
                    .describedAs("answered gaps of %s", id).containsExactlyElementsOf(gaps);

            GrammarTaskSpec spec = new GrammarTaskSpec(
                    id, GrammarExerciseKind.GAP_FILL, sentence, blanks, task.path("caseSensitive").asBoolean(false));
            List<String> reference = blanks.stream().map(blank -> blank.accepted().get(0)).toList();
            for (int gap = 0; gap < blanks.size(); gap++) {
                for (String accepted : blanks.get(gap).accepted()) {
                    List<String> answer = new ArrayList<>(reference);
                    answer.set(gap, accepted);
                    SubmissionOutcome outcome = engine.grade(spec, String.join("\n", answer));
                    assertThat(outcome.passed()).describedAs("%s with gap %d = %s", id, gap + 1, accepted).isTrue();
                }
            }
            SubmissionOutcome blank = engine.grade(spec, "\n".repeat(blanks.size() - 1) + "x");
            assertThat(blank.blanks().stream().filter(outcome -> outcome.correct()).count())
                    .describedAs("%s accepts an empty or junk answer", id).isZero();
        }
    }
}
