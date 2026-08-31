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

    @Test
    void aMissingLevelReadsAsMiddle() {
        assertThat(Level.orMiddle(null)).isEqualTo(Level.MIDDLE);
        assertThat(Level.orMiddle(Level.SENIOR)).isEqualTo(Level.SENIOR);
    }
}
