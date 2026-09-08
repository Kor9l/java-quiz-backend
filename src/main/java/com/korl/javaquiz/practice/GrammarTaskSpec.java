package com.korl.javaquiz.practice;

import java.util.List;

/**
 * Everything the engine needs to grade one grammar exercise, free of persistence concerns so
 * that the grading can be exercised without a database behind it — the same split
 * {@link TaskSpec} and {@link JavaTaskSpec} make on their tracks.
 *
 * @param id            identifies the exercise, used in messages about broken content
 * @param kind          what the learner is asked to do, and how the answer is read
 * @param sentence      the material: shuffled chunks on {@code WORD_ORDER}, a marked-up
 *                      sentence on the kinds that gap out part of it
 * @param blanks        the accepted answers, grouped by the blank they belong to
 * @param caseSensitive whether capitals are part of the answer
 */
public record GrammarTaskSpec(
        String id,
        GrammarExerciseKind kind,
        String sentence,
        List<Blank> blanks,
        boolean caseSensitive) {

    /**
     * One blank and every answer that counts as right for it. More than one is ordinary: two
     * word orders can both be good English, and which of them a learner reached is not the
     * exercise.
     */
    public record Blank(int index, List<String> accepted) {
    }

    /**
     * The chunks the learner is given, in the order the handout printed them. Derived rather
     * than stored, because the shuffled prompt and the chunk list are the same thing written
     * two ways, and storing both would let them disagree.
     */
    public List<String> chunks() {
        return GrammarAnswerMatcher.chunks(sentence);
    }
}
