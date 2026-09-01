package com.korl.javaquiz.english;

/**
 * Which side of a word the learner is shown, and which side they have to produce.
 *
 * <p>The two are not the same exercise: recognising {@code ongoing} is easier than recalling it
 * from «текущий», so the direction is picked per round rather than fixed for the module.
 */
public enum TranslationDirection {

    /** English shown, translation chosen. */
    EN_RU,

    /** Translation shown, English chosen. */
    RU_EN;

    public static TranslationDirection orEnRu(TranslationDirection direction) {
        return direction == null ? EN_RU : direction;
    }
}
