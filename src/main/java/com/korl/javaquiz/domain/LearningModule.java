package com.korl.javaquiz.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

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

    /**
     * The same reading, for the module inside a JSON request body — {@code POST /api/quiz/start}
     * carries it there rather than as a query parameter.
     *
     * <p>Without this, Jackson matches enum constants exactly, so the {@code english} the UI
     * sends everywhere else was rejected in the body alone. That rejection happens in the body
     * reader, before any resource method runs, so it never reached an exception mapper: the
     * grammar round failed on a 400 with no message in it at all.
     *
     * <p>Blank is null rather than an error — an absent module and an empty one both mean "not
     * given", and {@code requestedModule} is what turns that into backend.
     */
    @JsonCreator
    static LearningModule fromJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parse(value).orElseThrow(
                () -> new IllegalArgumentException("Unknown module: " + value));
    }
}
