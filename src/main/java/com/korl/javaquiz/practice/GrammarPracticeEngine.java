package com.korl.javaquiz.practice;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

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
        if (task.kind() == GrammarExerciseKind.GAP_FILL) {
            List<GrammarTaskSpec.Blank> blanks = requireGaps(task);
            List<String> lines = GrammarAnswerMatcher.lines(answer);
            if (lines.size() > blanks.size()) {
                return outcome(SubmissionStatus.SYNTAX_ERROR, "practice.error.tooManyGaps", List.of(), started);
            }
            boolean allFilled = lines.size() == blanks.size()
                    && lines.stream().noneMatch(String::isBlank);
            return allFilled
                    ? outcome(SubmissionStatus.PASSED, null, List.of(), started)
                    : outcome(SubmissionStatus.SYNTAX_ERROR, "practice.error.gapsUnfilled", List.of(), started);
        }
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
        if (task.kind() == GrammarExerciseKind.GAP_FILL) {
            return gradeGaps(task, answer, started);
        }
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

    /**
     * Grades every gap on its own, so a text with one wrong form out of eight says which one.
     * A gap left empty — or missing because the answer has fewer lines than the text has gaps —
     * is simply a wrong gap: the learner is told where they stand rather than refused outright.
     * More lines than gaps is refused, because then there is no telling which line was meant
     * for which gap.
     */
    private SubmissionOutcome gradeGaps(GrammarTaskSpec task, String answer, long started) {
        List<GrammarTaskSpec.Blank> blanks = requireGaps(task);
        List<String> lines = GrammarAnswerMatcher.lines(answer);
        if (lines.size() > blanks.size()) {
            return outcome(SubmissionStatus.SYNTAX_ERROR, "practice.error.tooManyGaps", List.of(), started);
        }
        List<BlankOutcome> outcomes = new ArrayList<>();
        boolean allCorrect = true;
        for (int i = 0; i < blanks.size(); i++) {
            GrammarTaskSpec.Blank blank = blanks.get(i);
            String line = i < lines.size() ? lines.get(i) : "";
            boolean correct = GrammarAnswerMatcher.matches(blank.accepted(), line, task.caseSensitive());
            outcomes.add(new BlankOutcome(blank.index(), correct));
            allCorrect &= correct;
        }
        return outcome(
                allCorrect ? SubmissionStatus.PASSED : SubmissionStatus.WRONG_RESULT,
                allCorrect ? null : "practice.diff.gapFill",
                List.copyOf(outcomes),
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

    /**
     * The blanks of a {@code GAP_FILL} exercise, in gap order, after checking they line up with
     * the text: gaps numbered 1..n in reading order, each with at least one accepted answer.
     * Broken content otherwise, and the content test is what keeps it from getting this far.
     */
    private static List<GrammarTaskSpec.Blank> requireGaps(GrammarTaskSpec task) {
        List<Integer> gaps = task.gaps();
        if (gaps.isEmpty()) {
            throw new IllegalStateException("Exercise " + task.id() + " marks no gaps to fill");
        }
        List<GrammarTaskSpec.Blank> blanks = task.blanks().stream()
                .sorted(Comparator.comparingInt(GrammarTaskSpec.Blank::index))
                .toList();
        List<Integer> indices = blanks.stream().map(GrammarTaskSpec.Blank::index).toList();
        List<Integer> expected = IntStream.rangeClosed(1, gaps.size()).boxed().toList();
        if (!gaps.equals(expected) || !indices.equals(expected)
                || blanks.stream().anyMatch(blank -> blank.accepted().isEmpty())) {
            throw new IllegalStateException("Exercise " + task.id()
                    + " has gaps " + gaps + " but answers for " + indices);
        }
        return blanks;
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
