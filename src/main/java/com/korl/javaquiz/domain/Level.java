package com.korl.javaquiz.domain;

import java.util.Arrays;
import java.util.List;

/**
 * How advanced the reader of a question or an article is assumed to be. Orthogonal to
 * {@link Difficulty}: difficulty says how tricky a question is, level says who is expected to
 * know the material at all. A junior can be asked a hard question about {@code ArrayList}
 * growth; ZGC tuning is not hard for a senior, it is simply not junior material.
 *
 * <p>Two ladders in one enum, one per {@link LearningModule}. Backend material is graded by
 * career level and English material by command of the language, and those are different words
 * for different things — "junior English" means nothing. What the two share is the shape: three
 * rungs, cumulative, damped one step at a time. That shape is the whole reason they are one enum
 * and one column.
 *
 * <p>Levels are cumulative <em>within their own module</em> — a track draws on its own level and
 * every level below it, so fundamentals stay in the senior pool instead of disappearing from it.
 *
 * <p>The rung is stored explicitly rather than read off {@code ordinal()}, and that is what
 * keeps the two ladders from being measured against each other: by ordinal, {@code BASE} would
 * count as sitting below {@code SENIOR} and an English track would drag in backend questions.
 */
public enum Level {
    JUNIOR(LearningModule.BACKEND, 0),
    MIDDLE(LearningModule.BACKEND, 1),
    SENIOR(LearningModule.BACKEND, 2),

    BASE(LearningModule.ENGLISH, 0),
    INTERMEDIATE(LearningModule.ENGLISH, 1),
    PRO(LearningModule.ENGLISH, 2);

    /** Damping applied one level below the track, so the tilt is strong but not exclusive. */
    private static final double STEP_DOWN = 0.5;

    private final LearningModule module;
    private final int rung;

    Level(LearningModule module, int rung) {
        this.module = module;
        this.rung = rung;
    }

    public LearningModule module() {
        return module;
    }

    /** Where this level sits on its own ladder, counting up from 0. */
    public int rung() {
        return rung;
    }

    /**
     * What a learner who has not chosen a level gets.
     *
     * <p>Middle for backend material, because that is the label every question written before
     * V7 carries and the default is what keeps them reachable. Base for English, because
     * someone who has not said otherwise starts at the beginning — there is no legacy content
     * to accommodate there.
     */
    public static Level defaultFor(LearningModule module) {
        return module == LearningModule.ENGLISH ? BASE : MIDDLE;
    }

    /**
     * The level to use when the incoming one is missing, or belongs to the other module's
     * ladder. The second case is the one worth guarding: a level off the wrong ladder passes
     * every type check and then matches nothing in the pool query, so the round comes out empty
     * rather than merely wrong, and an empty round looks like missing content.
     */
    public static Level orDefault(LearningModule module, Level level) {
        return level != null && level.module == module ? level : defaultFor(module);
    }

    /** The levels a track may draw on, for the {@code level in (...)} filter on the pool. */
    public List<Level> andBelow() {
        return Arrays.stream(values())
                .filter(candidate -> candidate.module == module && candidate.rung <= rung)
                .toList();
    }

    /**
     * Weight multiplier for content of this level inside the given track: same level 1.0, one
     * level below 0.5, two below 0.25. A senior session therefore leans senior while still
     * revisiting basics, and a junior session is never diluted at all — nothing sits below it.
     *
     * <p>Content above the track scores 1.0 rather than 0. It is already excluded by the pool
     * query, and silently zero-weighting it would turn a filtering bug into an empty quiz.
     * Content from the other module's ladder scores 1.0 for the same reason.
     */
    public double weightIn(Level track) {
        if (track.module != module) {
            return 1.0;
        }
        int stepsBelow = track.rung - rung;
        return stepsBelow <= 0 ? 1.0 : Math.pow(STEP_DOWN, stepsBelow);
    }
}
