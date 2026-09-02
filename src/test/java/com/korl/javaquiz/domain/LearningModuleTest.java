package com.korl.javaquiz.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The name travels in three spellings — {@code ?module=english} on the wire, {@code ENGLISH} in
 * the column, {@code ENGLISH} in the enum — and only the parse step reconciles them.
 */
class LearningModuleTest {

    @Test
    void theQueryParamSpellingIsAccepted() {
        assertThat(LearningModule.parse("english")).contains(LearningModule.ENGLISH);
        assertThat(LearningModule.parse("backend")).contains(LearningModule.BACKEND);
    }

    @Test
    void caseAndSurroundingSpaceDoNotMatter() {
        assertThat(LearningModule.parse("ENGLISH")).contains(LearningModule.ENGLISH);
        assertThat(LearningModule.parse("English")).contains(LearningModule.ENGLISH);
        assertThat(LearningModule.parse("  english  ")).contains(LearningModule.ENGLISH);
    }

    /**
     * Empty covers both "not given" and "not a module", because nothing here can tell which of
     * those is an error — the caller defaults the first and rejects the second.
     */
    @Test
    void anythingElseIsEmpty() {
        assertThat(LearningModule.parse(null)).isEmpty();
        assertThat(LearningModule.parse("")).isEmpty();
        assertThat(LearningModule.parse("   ")).isEmpty();
        assertThat(LearningModule.parse("grammar")).isEmpty();
        assertThat(LearningModule.parse("english-grammar")).isEmpty();
    }
}
