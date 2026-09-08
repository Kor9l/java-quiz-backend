package com.korl.javaquiz.practice;

/**
 * How one blank of a grammar exercise was answered.
 *
 * <p>Feedback is per blank rather than per exercise: an exercise with three gaps where the
 * second one is wrong should say so, because "wrong" sends the learner back over the two parts
 * they got right. A {@code WORD_ORDER} exercise has a single blank, so the list it comes back in
 * holds one entry — the same shape, read the same way, whatever the kind.
 *
 * @param index   which blank this is, numbered from 1 as the sentence numbers its markers
 * @param correct whether what arrived for it matched one of the accepted answers
 */
public record BlankOutcome(int index, boolean correct) {
}
