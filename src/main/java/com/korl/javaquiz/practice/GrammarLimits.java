package com.korl.javaquiz.practice;

/**
 * The one ceiling a grammar submission needs. There is no sandbox on this track and nothing
 * runs, so there is no timeout to set and no output to cap — what is left is refusing an answer
 * that is not an answer before it reaches the matcher.
 *
 * @param maxAnswerLength longest answer accepted at all; a sentence, not a document
 */
public record GrammarLimits(int maxAnswerLength) {

    public static GrammarLimits defaults() {
        return new GrammarLimits(600);
    }
}
