package com.korl.javaquiz.domain;

import java.util.Arrays;
import java.util.List;

/**
 * Career level a question or an article is written for. Orthogonal to {@link Difficulty}:
 * difficulty says how tricky a question is, level says who is expected to know the material at
 * all. A junior can be asked a hard question about {@code ArrayList} growth; ZGC tuning is not
 * hard for a senior, it is simply not junior material.
 *
 * <p>Levels are cumulative — a track draws on its own level and every level below it, so
 * fundamentals stay in the senior pool instead of disappearing from it.
 */
public enum Level {
    JUNIOR,
    MIDDLE,
    SENIOR;

    /** Damping applied one level below the track, so the tilt is strong but not exclusive. */
    private static final double STEP_DOWN = 0.5;

    public static Level orMiddle(Level level) {
        return level == null ? MIDDLE : level;
    }

    /** The levels a track may draw on, for the {@code level in (...)} filter on the pool. */
    public List<Level> andBelow() {
        return Arrays.stream(values()).filter(candidate -> candidate.ordinal() <= ordinal()).toList();
    }

    /**
     * Weight multiplier for content of this level inside the given track: same level 1.0, one
     * level below 0.5, two below 0.25. A senior session therefore leans senior while still
     * revisiting basics, and a junior session is never diluted at all — nothing sits below it.
     *
     * <p>Content above the track scores 1.0 rather than 0. It is already excluded by the pool
     * query, and silently zero-weighting it would turn a filtering bug into an empty quiz.
     */
    public double weightIn(Level track) {
        int stepsBelow = track.ordinal() - ordinal();
        return stepsBelow <= 0 ? 1.0 : Math.pow(STEP_DOWN, stepsBelow);
    }
}
