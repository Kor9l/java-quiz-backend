package com.korl.javaquiz.practice;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Grades grammar submissions: guard, then a check that the answer is built from the material the
 * exercise handed out, then a comparison with the answers it accepts.
 *
 * <p>The same shape as {@link SqlPracticeEngine} and {@link JavaPracticeEngine} — only the last
 * stage decides correctness — with the middle of all three missing, because there is nothing to
 * execute. That absence is the whole difference: no sandbox, no slot semaphore, no timeout and
 * no cached reference result. What a learner sends is a sentence, and comparing it costs
 * microseconds, so the ceremony the other two need to protect the process would protect nothing
 * here.
 */
@ApplicationScoped
public class GrammarPracticeEngine {

    private final GrammarLimits limits;

    public GrammarPracticeEngine(GrammarLimits limits) {
        this.limits = limits;
    }

    public GrammarLimits limits() {
        return limits;
    }

    /**
     * Reports whether the answer is made of the words the learner was given, and says nothing
     * about their order.
     *
     * <p>The analogue of parsing on the SQL track and compiling on the Java one: a shape check
     * a learner can ask for as often as they like without being told the answer. On this track
     * it is worth having for a specific reason — a word left behind in the chunk list is the
     * most common way to get one of these wrong, and it is not a word-order mistake at all.
     */
    public SubmissionOutcome check(GrammarTaskSpec task, String answer) {
        long started = System.nanoTime();
        guard(answer);
        List<String> chunks = requireChunks(task);
        if (!GrammarAnswerMatcher.usesGivenWords(chunks, answer, task.caseSensitive())) {
            return outcome(SubmissionStatus.SYNTAX_ERROR, "practice.error.notTheseWords", List.of(), started);
        }
        return outcome(SubmissionStatus.PASSED, null, List.of(), started);
    }

    /** Compares an answer with the ones the exercise accepts, and reports it blank by blank. */
    public SubmissionOutcome grade(GrammarTaskSpec task, String answer) {
        long started = System.nanoTime();
        guard(answer);
        List<String> chunks = requireChunks(task);
        GrammarTaskSpec.Blank blank = requireSingleBlank(task);
        // Told apart from a wrong order on purpose: a learner who used five of the six chunks
        // has not ordered anything wrongly, and hearing "wrong order" would send them looking
        // in the wrong place.
        if (!GrammarAnswerMatcher.usesGivenWords(chunks, answer, task.caseSensitive())) {
            return outcome(
                    SubmissionStatus.SYNTAX_ERROR,
                    "practice.error.notTheseWords",
                    List.of(new BlankOutcome(blank.index(), false)),
                    started);
        }
        boolean correct = GrammarAnswerMatcher.matches(blank.accepted(), answer, task.caseSensitive());
        return outcome(
                correct ? SubmissionStatus.PASSED : SubmissionStatus.WRONG_RESULT,
                correct ? null : "practice.diff.wordOrder",
                List.of(new BlankOutcome(blank.index(), correct)),
                started);
    }

    private void guard(String answer) {
        if (answer == null || answer.isBlank()) {
            throw new PracticeSubmissionException(SubmissionStatus.POLICY_ERROR, "practice.error.empty", null);
        }
        if (answer.length() > limits.maxAnswerLength()) {
            throw new PracticeSubmissionException(
                    SubmissionStatus.POLICY_ERROR, "practice.error.tooLong",
                    "limit=" + limits.maxAnswerLength());
        }
    }

    /**
     * Broken content rather than a learner's mistake, so it fails loudly instead of grading
     * everything wrong — and it is what the content test exists to keep from reaching here.
     */
    private static List<String> requireChunks(GrammarTaskSpec task) {
        if (task.kind() != GrammarExerciseKind.WORD_ORDER) {
            throw new IllegalStateException("Exercise " + task.id() + " is of a kind this engine cannot grade: "
                    + task.kind());
        }
        List<String> chunks = task.chunks();
        if (chunks.size() < 2) {
            throw new IllegalStateException("Exercise " + task.id() + " hands out nothing to order");
        }
        return chunks;
    }

    private static GrammarTaskSpec.Blank requireSingleBlank(GrammarTaskSpec task) {
        if (task.blanks().size() != 1 || task.blanks().get(0).accepted().isEmpty()) {
            throw new IllegalStateException(
                    "Exercise " + task.id() + " must accept at least one answer for exactly one blank");
        }
        return task.blanks().get(0);
    }

    private static SubmissionOutcome outcome(
            SubmissionStatus status, String messageKey, List<BlankOutcome> blanks, long started) {
        return new SubmissionOutcome(
                status, messageKey, null, null, null, null, elapsedMs(started), List.of(), List.of(), blanks);
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
