package com.korl.javaquiz.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LevelTest {

    @Test
    void aTrackDrawsOnItsOwnLevelAndEverythingBelowIt() {
        assertThat(Level.JUNIOR.andBelow()).containsExactly(Level.JUNIOR);
        assertThat(Level.MIDDLE.andBelow()).containsExactly(Level.JUNIOR, Level.MIDDLE);
        assertThat(Level.SENIOR.andBelow()).containsExactly(Level.JUNIOR, Level.MIDDLE, Level.SENIOR);
    }

    @Test
    void contentIsDampedOnceForEveryLevelBelowTheTrack() {
        assertThat(Level.SENIOR.weightIn(Level.SENIOR)).isEqualTo(1.0);
        assertThat(Level.MIDDLE.weightIn(Level.SENIOR)).isEqualTo(0.5);
        assertThat(Level.JUNIOR.weightIn(Level.SENIOR)).isEqualTo(0.25);
    }

    /** Nothing sits below junior, so a junior session is never diluted. */
    @Test
    void theJuniorTrackIsUndiluted() {
        assertThat(Level.JUNIOR.weightIn(Level.JUNIOR)).isEqualTo(1.0);
    }

    /**
     * Content above the track is excluded by the pool query, so the weight is never consulted.
     * It stays at 1.0 rather than 0 so a filtering bug shows up as an odd question rather than
     * as an empty quiz.
     */
    @Test
    void contentAboveTheTrackKeepsFullWeight() {
        assertThat(Level.SENIOR.weightIn(Level.JUNIOR)).isEqualTo(1.0);
    }

    /**
     * The English ladder is the same three rungs with the same damping. If this ever diverges
     * from the backend one, the two are no longer one mechanism and should stop being one enum.
     */
    @Test
    void theEnglishLadderBehavesExactlyLikeTheBackendOne() {
        assertThat(Level.BASE.andBelow()).containsExactly(Level.BASE);
        assertThat(Level.INTERMEDIATE.andBelow()).containsExactly(Level.BASE, Level.INTERMEDIATE);
        assertThat(Level.PRO.andBelow())
                .containsExactly(Level.BASE, Level.INTERMEDIATE, Level.PRO);

        assertThat(Level.PRO.weightIn(Level.PRO)).isEqualTo(1.0);
        assertThat(Level.INTERMEDIATE.weightIn(Level.PRO)).isEqualTo(0.5);
        assertThat(Level.BASE.weightIn(Level.PRO)).isEqualTo(0.25);
    }

    /**
     * The reason the rung is a field rather than {@code ordinal()}. By ordinal, BASE would sit
     * three places below SENIOR and a senior track would pull grammar into its pool.
     */
    @Test
    void theTwoLaddersDoNotSeeEachOther() {
        assertThat(Level.SENIOR.andBelow()).doesNotContain(Level.BASE, Level.INTERMEDIATE, Level.PRO);
        assertThat(Level.PRO.andBelow()).doesNotContain(Level.JUNIOR, Level.MIDDLE, Level.SENIOR);
    }

    /** Cross-ladder weight is full, on the same reasoning as content above the track. */
    @Test
    void aLevelFromTheOtherLadderKeepsFullWeight() {
        assertThat(Level.BASE.weightIn(Level.SENIOR)).isEqualTo(1.0);
        assertThat(Level.JUNIOR.weightIn(Level.PRO)).isEqualTo(1.0);
    }

    @Test
    void everyLevelKnowsWhichModuleItGrades() {
        assertThat(Level.JUNIOR.module()).isEqualTo(LearningModule.BACKEND);
        assertThat(Level.MIDDLE.module()).isEqualTo(LearningModule.BACKEND);
        assertThat(Level.SENIOR.module()).isEqualTo(LearningModule.BACKEND);
        assertThat(Level.BASE.module()).isEqualTo(LearningModule.ENGLISH);
        assertThat(Level.INTERMEDIATE.module()).isEqualTo(LearningModule.ENGLISH);
        assertThat(Level.PRO.module()).isEqualTo(LearningModule.ENGLISH);
    }

    /**
     * Middle for backend because that is what every question written before V7 is labelled;
     * base for English because someone who has not chosen starts at the beginning.
     */
    @Test
    void aMissingLevelReadsAsTheModuleDefault() {
        assertThat(Level.orDefault(LearningModule.BACKEND, null)).isEqualTo(Level.MIDDLE);
        assertThat(Level.orDefault(LearningModule.ENGLISH, null)).isEqualTo(Level.BASE);
    }

    @Test
    void aLevelOfTheRightModuleIsKept() {
        assertThat(Level.orDefault(LearningModule.BACKEND, Level.SENIOR)).isEqualTo(Level.SENIOR);
        assertThat(Level.orDefault(LearningModule.ENGLISH, Level.PRO)).isEqualTo(Level.PRO);
    }

    /**
     * The trap this guard exists for: a level off the wrong ladder type-checks, then matches
     * nothing in {@code level in (...)} and the round comes out empty rather than wrong — which
     * reads as missing content, not as a bug.
     */
    @Test
    void aLevelFromTheWrongLadderFallsBackToTheDefault() {
        assertThat(Level.orDefault(LearningModule.ENGLISH, Level.MIDDLE)).isEqualTo(Level.BASE);
        assertThat(Level.orDefault(LearningModule.BACKEND, Level.PRO)).isEqualTo(Level.MIDDLE);
    }
}
