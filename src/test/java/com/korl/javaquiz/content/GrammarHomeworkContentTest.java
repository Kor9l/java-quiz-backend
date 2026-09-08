package com.korl.javaquiz.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.korl.javaquiz.practice.GrammarAnswerMatcher;
import com.korl.javaquiz.practice.GrammarExerciseKind;
import com.korl.javaquiz.practice.GrammarLimits;
import com.korl.javaquiz.practice.GrammarPracticeEngine;
import com.korl.javaquiz.practice.GrammarTaskSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the homework course that {@code V20__LoadGrammarHomework} loads. The migration runs
 * once against a real database, so a missing translation, an orphan exercise or a level nobody
 * set would surface as a failed deployment; these checks catch it at build time.
 *
 * <p>Two of them are worth more than the rest, because they are the ones a proofreader misses.
 * A {@code WORD_ORDER} prompt has to be made of exactly the words its answer is made of — a
 * chunk mistyped or forgotten leaves an exercise nobody can solve — and the answer has to
 * actually pass the engine that will grade it. Both are checked by running the real matcher and
 * the real engine over the bundled content, the way
 * {@code JavaPracticeContentTest} compiles every bundled solution.
 */
class GrammarHomeworkContentTest {

    private static final String TOPIC_ID = "grammar-homework";
    private static final String BASE = "/content/english/grammar/homework/";
    private static final String THEORY_SECTION = "questions-direct-indirect";
    private static final String HOMEWORK_SECTION = "hw-questions-word-order";
    private static final int QUESTION_COUNT = 6;
    private static final int OPTIONS_PER_QUESTION = 5;
    private static final int EXERCISE_COUNT = 6;
    private static final Set<String> DIFFICULTIES = Set.of("easy", "medium", "hard");

    /** The English ladder, in order, so "does not go down the page" can be checked. */
    private static final List<String> ENGLISH_LEVELS = List.of("base", "intermediate", "pro");

    private static final Set<String> AREAS = Set.of(
            "syntax", "verbs", "nouns", "articles", "pronouns", "tenses",
            "modals", "adjectives", "prepositions", "quantifiers");

    private static JsonNode topic;
    private static JsonNode materials;
    private static JsonNode questions;
    private static JsonNode exercises;
    private static List<String> sectionIds;

    private final GrammarPracticeEngine engine = new GrammarPracticeEngine(GrammarLimits.defaults());

    @BeforeAll
    static void load() throws Exception {
        topic = read(BASE + "topic.json").get("topic");
        materials = read(BASE + "materials.json");
        questions = read(BASE + "questions.json");
        exercises = read(BASE + "exercises.json");
        sectionIds = new ArrayList<>();
        topic.get("sections").forEach(section -> sectionIds.add(section.get("id").asText()));
    }

    private static JsonNode read(String path) throws Exception {
        try (InputStream in = GrammarHomeworkContentTest.class.getResourceAsStream(path)) {
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

    private static List<JsonNode> taskList() {
        return list(exercises.get("tasks"));
    }

    private static void assertBilingual(JsonNode node, String field, String described) {
        for (String language : List.of("en", "ru")) {
            assertThat(node.path(field).path(language).asText(""))
                    .describedAs("%s %s (%s)", described, field, language).isNotBlank();
        }
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
        // Ordering is within a module, and grammar-base is first.
        assertThat(topic.get("order").asInt()).isEqualTo(2);
        assertBilingual(topic, "name", TOPIC_ID);
    }

    @Test
    void theCourseDeclaresItsSectionsInOrder() {
        assertThat(sectionIds).doesNotHaveDuplicates().containsExactly(THEORY_SECTION, HOMEWORK_SECTION);

        int expected = 1;
        for (JsonNode section : sections()) {
            String id = section.get("id").asText();
            assertThat(section.get("order").asInt()).describedAs("order of %s", id).isEqualTo(expected++);
            assertBilingual(section, "title", id);
        }
    }

    /**
     * Unlike the CEFR courses, a homework course is not one level end to end: it fills up in
     * lesson order, and the next handout may sit anywhere on the ladder. What still has to hold
     * is that the level is stated, that it is a level from <em>this</em> module — a backend grade
     * here would put the row in a pool no English track ever queries, so the round would come
     * out empty rather than odd — and that reading down the page never goes backwards.
     */
    @Test
    void everySectionStatesALevelFromTheEnglishLadderAndCarriesAnArea() {
        int previous = -1;
        for (JsonNode section : sections()) {
            String id = section.get("id").asText();
            assertThat(section.hasNonNull("level")).describedAs("level of %s is missing", id).isTrue();
            String level = section.get("level").asText();
            assertThat(ENGLISH_LEVELS).describedAs("level of %s", id).contains(level);
            assertThat(ENGLISH_LEVELS.indexOf(level))
                    .describedAs("level of %s goes back down the page", id)
                    .isGreaterThanOrEqualTo(previous);
            previous = ENGLISH_LEVELS.indexOf(level);

            assertThat(section.hasNonNull("area")).describedAs("area of %s is missing", id).isTrue();
            assertThat(AREAS).describedAs("area of %s", id).contains(section.get("area").asText());
        }
    }

    @Test
    void everySectionHasAnArticleInBothLanguages() {
        List<String> withMaterial = new ArrayList<>();
        for (JsonNode section : list(materials.get("sections"))) {
            String id = section.get("id").asText();
            withMaterial.add(id);
            assertBilingual(section, "summary", id);
            assertBilingual(section, "body", id);
            // The same range the rest of the material sits in, so the estimate stays comparable.
            assertThat(section.get("estimatedMinutes").asInt())
                    .describedAs("estimatedMinutes of %s", id).isBetween(11, 18);
            assertThat(list(section.get("sources"))).describedAs("sources of %s", id).hasSizeGreaterThan(1);
            for (JsonNode source : section.get("sources")) {
                assertThat(source.get("title").asText()).describedAs("source title in %s", id).isNotBlank();
                assertThat(source.get("url").asText()).describedAs("source url in %s", id).startsWith("https://");
            }
        }
        assertThat(withMaterial).containsExactlyElementsOf(sectionIds);
    }

    /**
     * The theory section carries a quiz like every other grammar section; the handout section
     * carries exercises instead, because assembling the questions is its quiz. Both halves of
     * that are asserted, since a question quietly attached to the handout section would show up
     * as a quiz the learner is offered before they have done the homework.
     */
    @Test
    void onlyTheTheorySectionCarriesQuizQuestions() {
        assertThat(questionList()).hasSize(QUESTION_COUNT);
        assertThat(questionList().stream().map(question -> question.get("section").asText()).distinct())
                .containsExactly(THEORY_SECTION);
    }

    @Test
    void everyQuestionIsWellFormedAndStatesItsOwnLevel() {
        List<String> ids = new ArrayList<>();
        for (JsonNode question : questionList()) {
            String id = question.get("id").asText();
            ids.add(id);
            assertThat(id).describedAs("id of %s", id).startsWith(TOPIC_ID + "." + THEORY_SECTION + ".");
            assertThat(DIFFICULTIES).describedAs("difficulty of %s", id)
                    .contains(question.get("difficulty").asText());
            assertThat(question.hasNonNull("level")).describedAs("level of %s is missing", id).isTrue();
            assertThat(ENGLISH_LEVELS).describedAs("level of %s", id).contains(question.get("level").asText());
            assertBilingual(question, "text", id);
            assertBilingual(question, "explanation", id);
            assertThat(list(question.get("sources"))).describedAs("sources of %s", id).isNotEmpty();

            List<JsonNode> options = list(question.get("options"));
            assertThat(options).describedAs("options of %s", id).hasSize(OPTIONS_PER_QUESTION);
            List<String> texts = new ArrayList<>();
            long correct = 0;
            for (JsonNode option : options) {
                assertBilingual(option, "text", id);
                texts.add(option.get("text").get("en").asText());
                correct += option.get("correct").asBoolean() ? 1 : 0;
            }
            assertThat(correct).describedAs("correct options of %s", id).isEqualTo(1);
            assertThat(texts).describedAs("options of %s repeat", id).doesNotHaveDuplicates();
        }
        assertThat(ids).doesNotHaveDuplicates();
        // A mix rather than six of a kind, the way the rest of the grammar content is graded.
        assertThat(questionList().stream().map(question -> question.get("difficulty").asText()).distinct())
                .hasSizeGreaterThan(1);
    }

    @Test
    void theExercisesBelongToTheGrammarTrackAndToTheHandoutSection() {
        assertThat(exercises.get("track").asText()).isEqualTo("grammar");
        assertThat(taskList()).hasSize(EXERCISE_COUNT);

        for (JsonNode task : taskList()) {
            String id = task.get("id").asText();
            assertThat(task.get("topic").asText()).describedAs("topic of %s", id).isEqualTo(TOPIC_ID);
            assertThat(task.get("section").asText()).describedAs("section of %s", id).isEqualTo(HOMEWORK_SECTION);
            assertThat(task.get("kind").asText().toUpperCase(Locale.ROOT))
                    .describedAs("kind of %s", id)
                    .isEqualTo(GrammarExerciseKind.WORD_ORDER.name());
        }
    }

    /**
     * The track is navigated by difficulty, and the order restarts inside each one — the same
     * numbering the SQL and Java tracks use, since it is the same endpoint reading it.
     */
    @Test
    void theExercisesAreNumberedWithinEachDifficulty() {
        Map<String, List<Integer>> orders = new LinkedHashMap<>();
        List<String> ids = new ArrayList<>();
        for (JsonNode task : taskList()) {
            String id = task.get("id").asText();
            ids.add(id);
            String difficulty = task.get("difficulty").asText();
            assertThat(DIFFICULTIES).describedAs("difficulty of %s", id).contains(difficulty);
            assertThat(id).describedAs("id of %s does not name its difficulty", id)
                    .startsWith("grammar-" + difficulty + "-");
            orders.computeIfAbsent(difficulty, key -> new ArrayList<>()).add(task.get("order").asInt());
        }
        assertThat(ids).doesNotHaveDuplicates();
        // All three buckets in use: a track with everything in one of them navigates to a single
        // screen, and the difficulty picker in front of it would be a lie.
        assertThat(orders.keySet()).containsExactlyInAnyOrderElementsOf(DIFFICULTIES);
        orders.forEach((difficulty, values) -> {
            List<Integer> expected = new ArrayList<>();
            for (int i = 1; i <= values.size(); i++) {
                expected.add(i);
            }
            assertThat(values).describedAs("orders of %s", difficulty).containsExactlyElementsOf(expected);
        });
    }

    @Test
    void everyExerciseIsWrittenInBothLanguages() {
        for (JsonNode task : taskList()) {
            String id = task.get("id").asText();
            assertBilingual(task, "title", id);
            assertBilingual(task, "statement", id);
            // Unlike the other two tracks, the hint is not optional here: a word-order exercise
            // a learner cannot see into offers nothing to try except permutations.
            assertBilingual(task, "hint", id);
            assertBilingual(task, "explanation", id);
            assertThat(list(task.get("sources"))).describedAs("sources of %s", id).isNotEmpty();
            for (JsonNode source : task.get("sources")) {
                assertThat(source.get("title").asText()).describedAs("source title in %s", id).isNotBlank();
                assertThat(source.get("url").asText()).describedAs("source url in %s", id).startsWith("https://");
            }
        }
    }

    /**
     * The check a proofreader misses. A prompt is a handout typed out by hand, and a chunk
     * mistyped, doubled or forgotten leaves an exercise whose answer cannot be assembled from
     * what the learner is given — which no amount of reading the answer aloud reveals.
     */
    @Test
    void everyAcceptedAnswerIsMadeOfExactlyTheChunksHandedOut() {
        for (JsonNode task : taskList()) {
            String id = task.get("id").asText();
            List<String> chunks = GrammarAnswerMatcher.chunks(task.get("sentence").asText());
            assertThat(chunks).describedAs("chunks of %s", id).hasSizeGreaterThan(1);
            assertThat(chunks).describedAs("chunks of %s", id).allSatisfy(chunk ->
                    assertThat(chunk).isNotBlank());

            List<JsonNode> blanks = list(task.get("blanks"));
            assertThat(blanks).describedAs("blanks of %s", id).hasSize(1);
            assertThat(blanks.get(0).get("index").asInt()).describedAs("blank index of %s", id).isEqualTo(1);
            List<JsonNode> accepted = list(blanks.get(0).get("accepted"));
            assertThat(accepted).describedAs("accepted answers of %s", id).isNotEmpty();

            for (JsonNode answer : accepted) {
                String text = answer.asText();
                assertThat(text).describedAs("accepted answer of %s", id).isNotBlank();
                assertThat(GrammarAnswerMatcher.usesGivenWords(chunks, text, false))
                        .describedAs("accepted answer of %s is not made of its own chunks: %s", id, text)
                        .isTrue();
            }
        }
    }

    /**
     * The exercise has to be an exercise: a prompt whose chunks are already in the right order
     * is solved by pressing Run.
     */
    @Test
    void noPromptIsAlreadyItsOwnAnswer() {
        for (JsonNode task : taskList()) {
            String id = task.get("id").asText();
            List<String> chunks = GrammarAnswerMatcher.chunks(task.get("sentence").asText());
            List<String> accepted = new ArrayList<>();
            list(task.get("blanks").get(0).get("accepted")).forEach(answer -> accepted.add(answer.asText()));

            assertThat(GrammarAnswerMatcher.matches(accepted, String.join(" ", chunks), false))
                    .describedAs("prompt of %s is already in the right order", id)
                    .isFalse();
        }
    }

    /** Every bundled answer, run through the engine that will grade the learner's. */
    @Test
    void theEnginePassesEveryBundledAnswer() {
        for (JsonNode task : taskList()) {
            String id = task.get("id").asText();
            GrammarTaskSpec spec = spec(task);
            for (String answer : spec.blanks().get(0).accepted()) {
                assertThat(engine.grade(spec, answer).passed())
                        .describedAs("engine rejects the bundled answer of %s: %s", id, answer)
                        .isTrue();
            }
        }
    }

    private static GrammarTaskSpec spec(JsonNode task) {
        JsonNode blank = task.get("blanks").get(0);
        List<String> accepted = new ArrayList<>();
        blank.get("accepted").forEach(answer -> accepted.add(answer.asText()));
        return new GrammarTaskSpec(
                task.get("id").asText(),
                GrammarExerciseKind.valueOf(task.get("kind").asText().toUpperCase(Locale.ROOT)),
                task.get("sentence").asText(),
                List.of(new GrammarTaskSpec.Blank(blank.get("index").asInt(), List.copyOf(accepted))),
                task.path("caseSensitive").asBoolean(false));
    }
}
