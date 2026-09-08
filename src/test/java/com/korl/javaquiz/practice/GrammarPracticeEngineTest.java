package com.korl.javaquiz.practice;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrammarPracticeEngineTest {

    private static final String PROMPT = "the Black Horse restaurant / if / tell / you / near here / is / Could / me";
    private static final String ANSWER = "Could you tell me if the Black Horse restaurant is near here?";

    private final GrammarPracticeEngine engine = new GrammarPracticeEngine(GrammarLimits.defaults());

    private static GrammarTaskSpec task(String... accepted) {
        return new GrammarTaskSpec(
                "grammar-hard-02",
                GrammarExerciseKind.WORD_ORDER,
                PROMPT,
                List.of(new GrammarTaskSpec.Blank(1, List.of(accepted))),
                false);
    }

    @Test
    void passesTheReferenceAnswer() {
        SubmissionOutcome outcome = engine.grade(task(ANSWER), ANSWER);

        assertThat(outcome.passed()).isTrue();
        assertThat(outcome.status()).isEqualTo(SubmissionStatus.PASSED);
        assertThat(outcome.messageKey()).isNull();
        assertThat(outcome.blanks()).containsExactly(new BlankOutcome(1, true));
    }

    @Test
    void passesAnAnswerTypedWithoutTheCapitalOrTheQuestionMark() {
        SubmissionOutcome outcome = engine.grade(
                task(ANSWER), "could you tell me if the black horse restaurant is near here");

        assertThat(outcome.passed()).isTrue();
    }

    @Test
    void reportsAWrongOrderAsAWrongResult() {
        SubmissionOutcome outcome = engine.grade(
                task(ANSWER), "Could you tell me if is the Black Horse restaurant near here?");

        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.status()).isEqualTo(SubmissionStatus.WRONG_RESULT);
        assertThat(outcome.messageKey()).isEqualTo("practice.diff.wordOrder");
        assertThat(outcome.blanks()).containsExactly(new BlankOutcome(1, false));
    }

    /**
     * Told apart from a wrong order on purpose: a learner who dropped a chunk has not ordered
     * anything wrongly, and "wrong order" would send them looking in the wrong place.
     */
    @Test
    void reportsAMissingChunkAsSomethingOtherThanAWrongOrder() {
        SubmissionOutcome outcome = engine.grade(
                task(ANSWER), "Could you tell me the Black Horse restaurant is near here?");

        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.status()).isEqualTo(SubmissionStatus.SYNTAX_ERROR);
        assertThat(outcome.messageKey()).isEqualTo("practice.error.notTheseWords");
    }

    @Test
    void takesAnyOfSeveralAcceptedAnswers() {
        GrammarTaskSpec spec = new GrammarTaskSpec(
                "grammar-medium-02",
                GrammarExerciseKind.WORD_ORDER,
                "does / go / school / your daughter / Which / to",
                List.of(new GrammarTaskSpec.Blank(1, List.of(
                        "Which school does your daughter go to?",
                        "To which school does your daughter go?"))),
                false);

        assertThat(engine.grade(spec, "To which school does your daughter go?").passed()).isTrue();
    }

    /**
     * The shape check, and the whole of what {@code check} promises: it says whether the answer
     * is made of the words handed out, and nothing about whether they are in the right order.
     * A learner may press it as often as they like without being told the answer.
     */
    @Test
    void checkPassesAWrongOrderMadeOfTheRightWords() {
        SubmissionOutcome outcome = engine.check(
                task(ANSWER), "Could you tell me if is the Black Horse restaurant near here?");

        assertThat(outcome.passed()).isTrue();
        assertThat(outcome.blanks()).isEmpty();
    }

    @Test
    void checkRefusesAWordThatWasNeverHandedOut() {
        SubmissionOutcome outcome = engine.check(
                task(ANSWER), "Could you please tell me if the Black Horse restaurant is near here?");

        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.status()).isEqualTo(SubmissionStatus.SYNTAX_ERROR);
        assertThat(outcome.messageKey()).isEqualTo("practice.error.notTheseWords");
    }

    @Test
    void refusesAnEmptySubmission() {
        assertThatThrownBy(() -> engine.grade(task(ANSWER), "   "))
                .isInstanceOf(PracticeSubmissionException.class)
                .extracting(thrown -> ((PracticeSubmissionException) thrown).getMessageKey())
                .isEqualTo("practice.error.empty");
    }

    @Test
    void refusesASubmissionLongerThanASentence() {
        String flood = ANSWER.repeat(20);

        assertThatThrownBy(() -> engine.grade(task(ANSWER), flood))
                .isInstanceOf(PracticeSubmissionException.class)
                .extracting(thrown -> ((PracticeSubmissionException) thrown).getStatus())
                .isEqualTo(SubmissionStatus.POLICY_ERROR);
    }

    /**
     * Broken content rather than a learner's mistake, so it fails loudly instead of grading
     * every answer wrong.
     */
    @Test
    void refusesToGradeAnExerciseThatAcceptsNothing() {
        GrammarTaskSpec spec = new GrammarTaskSpec(
                "grammar-easy-99", GrammarExerciseKind.WORD_ORDER, PROMPT, List.of(), false);

        assertThatThrownBy(() -> engine.grade(spec, ANSWER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("grammar-easy-99");
    }

    /**
     * The fields the other two tracks fill are present and empty here, so a client can read a
     * response without knowing which track produced it.
     */
    @Test
    void leavesTheOtherTracksFieldsEmptyRatherThanNull() {
        SubmissionOutcome outcome = engine.grade(task(ANSWER), ANSWER);

        assertThat(outcome.diagnostics()).isEmpty();
        assertThat(outcome.output()).isEmpty();
        assertThat(outcome.result()).isNull();
        assertThat(outcome.expected()).isNull();
    }
}
