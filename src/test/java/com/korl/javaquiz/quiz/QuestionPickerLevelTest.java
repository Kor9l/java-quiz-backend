package com.korl.javaquiz.quiz;

import com.korl.javaquiz.domain.Difficulty;
import com.korl.javaquiz.domain.Level;
import com.korl.javaquiz.domain.Question;
import com.korl.javaquiz.userstate.StatsPayload;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * The level filter on the pool decides what a track may see; this weighting decides what it
 * actually gets. Both matter: without the weighting a senior session drawn from a cumulative
 * pool would be a third junior questions.
 */
class QuestionPickerLevelTest {

    private static final StatsPayload NO_HISTORY = new StatsPayload();

    @Test
    void anUnseenQuestionIsDampedByHowFarBelowTheTrackItSits() {
        QuestionPicker picker = new QuestionPicker(new Random(1), Level.SENIOR);
        double senior = picker.weightOf(question("s", Difficulty.MEDIUM, Level.SENIOR), NO_HISTORY);
        double middle = picker.weightOf(question("m", Difficulty.MEDIUM, Level.MIDDLE), NO_HISTORY);
        double junior = picker.weightOf(question("j", Difficulty.MEDIUM, Level.JUNIOR), NO_HISTORY);

        assertThat(senior).isEqualTo(QuestionPicker.UNSEEN_WEIGHT * Difficulty.MEDIUM.weight());
        assertThat(middle).isEqualTo(senior * 0.5);
        assertThat(junior).isEqualTo(senior * 0.25);
    }

    /** Difficulty and level are independent multipliers, not two names for the same axis. */
    @Test
    void difficultyStillCountsWithinALevel() {
        QuestionPicker picker = new QuestionPicker(new Random(1), Level.SENIOR);
        double easySenior = picker.weightOf(question("a", Difficulty.EASY, Level.SENIOR), NO_HISTORY);
        double hardSenior = picker.weightOf(question("b", Difficulty.HARD, Level.SENIOR), NO_HISTORY);

        assertThat(hardSenior).isGreaterThan(easySenior);
    }

    @Test
    void aSeniorSessionLeansSeniorWithoutDroppingRevision() {
        List<Question> pool = List.of(
                question("junior", Difficulty.MEDIUM, Level.JUNIOR),
                question("senior", Difficulty.MEDIUM, Level.SENIOR));
        QuestionPicker picker = new QuestionPicker(new Random(20260831), Level.SENIOR);

        int seniorPicks = 0;
        int draws = 1000;
        for (int i = 0; i < draws; i++) {
            seniorPicks += picker.pick(pool, 1, true, NO_HISTORY).get(0).getId().equals("senior") ? 1 : 0;
        }

        // Weights are 4:1, so roughly 800 of 1000. The bounds only pin the tilt and the fact
        // that junior material still surfaces.
        assertThat(seniorPicks).isBetween(700, 900);
    }

    /**
     * The picker is told a track, not a module, and it never needs to know one: the damping is
     * a property of the ladder the two levels share. This is what makes one picker serve both.
     */
    @Test
    void anEnglishTrackIsDampedOnTheEnglishLadder() {
        QuestionPicker picker = new QuestionPicker(new Random(1), Level.PRO);
        double pro = picker.weightOf(question("p", Difficulty.MEDIUM, Level.PRO), NO_HISTORY);
        double intermediate = picker.weightOf(question("i", Difficulty.MEDIUM, Level.INTERMEDIATE), NO_HISTORY);
        double base = picker.weightOf(question("b", Difficulty.MEDIUM, Level.BASE), NO_HISTORY);

        assertThat(pro).isEqualTo(QuestionPicker.UNSEEN_WEIGHT * Difficulty.MEDIUM.weight());
        assertThat(intermediate).isEqualTo(pro * 0.5);
        assertThat(base).isEqualTo(pro * 0.25);
    }

    /**
     * A track is required rather than defaulted. The old fallback was middle, which is a
     * backend level: an English round that lost its track would have kept working with every
     * question weighted the same, and nothing would have said so.
     */
    @Test
    void aMissingTrackIsRejectedRatherThanDefaulted() {
        assertThatNullPointerException().isThrownBy(() -> new QuestionPicker(new Random(1), null));
    }

    @Test
    void aJuniorSessionSeesNoDamping() {
        QuestionPicker picker = new QuestionPicker(new Random(1), Level.JUNIOR);
        double weight = picker.weightOf(question("j", Difficulty.EASY, Level.JUNIOR), NO_HISTORY);

        assertThat(weight).isEqualTo(QuestionPicker.UNSEEN_WEIGHT * Difficulty.EASY.weight());
    }

    /** Question has no setters — it is only ever built by Hibernate or by a migration. */
    private static Question question(String id, Difficulty difficulty, Level level) {
        try {
            Question question = Question.class.getDeclaredConstructor().newInstance();
            set(question, "id", id);
            set(question, "difficulty", difficulty);
            set(question, "level", level);
            return question;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot build a Question for the test", e);
        }
    }

    private static void set(Question question, String name, Object value) throws ReflectiveOperationException {
        Field field = Question.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(question, value);
    }
}
