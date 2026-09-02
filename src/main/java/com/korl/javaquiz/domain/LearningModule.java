package com.korl.javaquiz.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * The two things this app teaches, which is the choice the learner makes right after signing in.
 *
 * <p>A discriminator rather than a second set of tables: backend material and English grammar
 * are the same shape — a topic of sections, an article per section, questions per section — and
 * they share the quiz, the read state and the stats that hang off it. What differs is only who
 * should be shown which, and that is one column.
 *
 * <p>The English module has two halves: the vocabulary trainer that came over from the words app
 * and the grammar courses. Only grammar is made of topics, so only grammar needs this column;
 * the words live in their own tables and never touch it.
 */
public enum LearningModule {
    BACKEND,
    ENGLISH;

    /**
     * Reads the value a client sent as {@code ?module=}, case-insensitively — the API spells it
     * {@code english} and the column spells it {@code ENGLISH}. Empty rather than an exception,
     * so the caller decides what an unknown name costs; nothing here knows whether a missing
     * module is a default or a mistake.
     */
    public static Optional<LearningModule> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        return Arrays.stream(values())
                .filter(module -> module.name().equalsIgnoreCase(trimmed))
                .findFirst();
    }
}
