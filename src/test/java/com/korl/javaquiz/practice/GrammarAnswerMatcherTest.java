package com.korl.javaquiz.practice;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The matcher is the whole of correctness on the grammar track, so what it forgives and what it
 * refuses is the specification of the track rather than a detail of it. Every case here is one a
 * learner produces: a lower-case first word, a missing question mark, a phone's curly
 * apostrophe, a chunk left behind in the list.
 */
class GrammarAnswerMatcherTest {

    private static final String PROMPT = "you / the / a long time / Have / for / known / Swanns";
    private static final String ANSWER = "Have you known the Swanns for a long time?";

    @Test
    void splitsAPromptIntoChunksAndKeepsMultiWordOnesWhole() {
        assertThat(GrammarAnswerMatcher.chunks(PROMPT))
                .containsExactly("you", "the", "a long time", "Have", "for", "known", "Swanns");
    }

    @Test
    void dropsEmptyChunksAndTrimsTheRest() {
        assertThat(GrammarAnswerMatcher.chunks("  Could /  / you  tell /me "))
                .containsExactly("Could", "you tell", "me");
    }

    @Test
    void acceptsTheAnswerAsWritten() {
        assertThat(GrammarAnswerMatcher.matches(List.of(ANSWER), ANSWER, false)).isTrue();
    }

    @Test
    void forgivesWhatTheChunksNeverGaveTheLearner() {
        // No capital, no question mark, doubled spaces: none of the three is among the chunks,
        // so none of them can be part of the answer.
        assertThat(GrammarAnswerMatcher.matches(
                List.of(ANSWER), "have you  known the swanns for a long time", false)).isTrue();
    }

    @Test
    void forgivesAPhonesApostrophe() {
        assertThat(GrammarAnswerMatcher.matches(
                List.of("Could you tell me if it isn't far?"), "could you tell me if it isn’t far", false))
                .isTrue();
    }

    @Test
    void keepsCaseWhenTheExerciseIsAboutCase() {
        assertThat(GrammarAnswerMatcher.matches(List.of(ANSWER), ANSWER, true)).isTrue();
        assertThat(GrammarAnswerMatcher.matches(
                List.of(ANSWER), "have you known the Swanns for a long time?", true)).isFalse();
    }

    @Test
    void refusesADifferentOrderOfTheSameWords() {
        assertThat(GrammarAnswerMatcher.matches(
                List.of(ANSWER), "Have you known the Swanns a long time for?", false)).isFalse();
    }

    @Test
    void refusesAnEmptyAnswer() {
        assertThat(GrammarAnswerMatcher.matches(List.of(ANSWER), "   ", false)).isFalse();
        assertThat(GrammarAnswerMatcher.matches(List.of(ANSWER), null, false)).isFalse();
    }

    @Test
    void takesAnyOfSeveralAcceptedAnswers() {
        List<String> accepted = List.of(
                "Which school does your daughter go to?",
                "To which school does your daughter go?");

        assertThat(GrammarAnswerMatcher.matches(accepted, "to which school does your daughter go", false))
                .isTrue();
    }

    @Test
    void seesThatAWrongOrderStillUsesTheRightWords() {
        List<String> chunks = GrammarAnswerMatcher.chunks(PROMPT);

        assertThat(GrammarAnswerMatcher.usesGivenWords(chunks, ANSWER, false)).isTrue();
        assertThat(GrammarAnswerMatcher.usesGivenWords(
                chunks, "Have you known the Swanns a long time for", false)).isTrue();
    }

    @Test
    void seesAChunkLeftBehindAndAWordThatWasNeverGiven() {
        List<String> chunks = GrammarAnswerMatcher.chunks(PROMPT);

        assertThat(GrammarAnswerMatcher.usesGivenWords(
                chunks, "Have you known the Swanns for a long", false)).isFalse();
        assertThat(GrammarAnswerMatcher.usesGivenWords(
                chunks, "Have you really known the Swanns for a long time", false)).isFalse();
    }

    /**
     * A learner who used every word but grouped it differently is answering the exercise, not
     * dodging it: the chunks are how the words were handed out, not part of the answer.
     */
    @Test
    void countsWordsRatherThanChunks() {
        List<String> chunks = GrammarAnswerMatcher.chunks("your daughter / Which school / does / go to");

        assertThat(GrammarAnswerMatcher.usesGivenWords(
                chunks, "Which school does your daughter go to?", false)).isTrue();
    }
}
