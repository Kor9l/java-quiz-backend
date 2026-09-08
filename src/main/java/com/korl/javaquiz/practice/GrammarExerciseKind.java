package com.korl.javaquiz.practice;

/**
 * What a grammar exercise asks the learner to do, and therefore how its answer is read.
 *
 * <p>Only the kinds that have content are here. The plan lists five more — {@code GAP_FILL},
 * {@code CHOICE_IN_CONTEXT}, {@code TRANSFORM}, {@code ERROR_CORRECTION} and
 * {@code TRANSLATE_RU_EN} — and each of them arrives with the exercises that need it: a value
 * this engine cannot grade would be a promise the API does not keep.
 */
public enum GrammarExerciseKind {

    /**
     * Assemble a phrase from shuffled chunks. The chunks are the whole of the material, so the
     * only thing being tested is the order they go in.
     */
    WORD_ORDER
}
