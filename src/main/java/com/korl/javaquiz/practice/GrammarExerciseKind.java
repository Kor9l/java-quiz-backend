package com.korl.javaquiz.practice;

/**
 * What a grammar exercise asks the learner to do, and therefore how its answer is read.
 *
 * <p>Only the kinds that have content are here. The plan lists four more —
 * {@code CHOICE_IN_CONTEXT}, {@code TRANSFORM}, {@code ERROR_CORRECTION} and
 * {@code TRANSLATE_RU_EN} — and each of them arrives with the exercises that need it: a value
 * this engine cannot grade would be a promise the API does not keep.
 */
public enum GrammarExerciseKind {

    /**
     * Assemble a phrase from shuffled chunks. The chunks are the whole of the material, so the
     * only thing being tested is the order they go in.
     */
    WORD_ORDER,

    /**
     * Fill the gaps in a text with the right form. The text marks each gap with {@code {{n}}},
     * numbered from 1, and usually gives the word to put into form in brackets after it — the
     * way a grammar book prints "I {{1}} (wait) for two hours". The answer is one line per gap,
     * in the order the gaps are numbered, and each gap is graded on its own.
     */
    GAP_FILL
}
