package com.korl.javaquiz.english;

import com.korl.javaquiz.domain.Word;
import com.korl.javaquiz.userstate.WordStatsPayload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Chooses which words a round asks, the way {@link com.korl.javaquiz.quiz.QuestionPicker} does
 * for the backend quiz: weighted sampling without replacement, favouring what the learner has
 * never seen or keeps getting wrong.
 *
 * <p>The weights read {@link WordStatsPayload}, which is per learner. The {@code correctCount}
 * on the word itself is the history that came over with the import — one global pair shared by
 * everybody — and deliberately does not steer anyone's round.
 */
public class WordPicker {

    static final double UNSEEN_WEIGHT = 4.0;
    static final double MASTERED_WEIGHT = 0.35;
    static final double RECENT_MISS_BONUS = 2.0;

    private final Random random;

    public WordPicker(Random random) {
        this.random = random;
    }

    public List<Word> pick(List<Word> pool, int count, boolean smart, WordStatsPayload stats) {
        List<Word> candidates = new ArrayList<>(pool);
        int take = Math.min(count, candidates.size());
        if (take <= 0) {
            return List.of();
        }
        if (!smart || stats == null) {
            Collections.shuffle(candidates, random);
            return new ArrayList<>(candidates.subList(0, take));
        }

        List<Double> weights = new ArrayList<>(candidates.size());
        for (Word word : candidates) {
            weights.add(weightOf(word, stats));
        }
        List<Word> chosen = new ArrayList<>(take);
        for (int i = 0; i < take; i++) {
            int index = sampleIndex(weights);
            chosen.add(candidates.get(index));
            candidates.remove(index);
            weights.remove(index);
        }
        return chosen;
    }

    double weightOf(Word word, WordStatsPayload stats) {
        WordStatsPayload.WordCounter counter = stats.words.get(word.getId().toString());
        if (counter == null || counter.answered == 0) {
            return UNSEEN_WEIGHT;
        }
        double wrongRate = 1.0 - counter.accuracy();
        double weight = MASTERED_WEIGHT + 2.5 * wrongRate;
        if (!counter.lastCorrect) {
            weight += RECENT_MISS_BONUS;
        }
        return weight;
    }

    private int sampleIndex(List<Double> weights) {
        double total = 0.0;
        for (double weight : weights) {
            total += weight;
        }
        if (total <= 0.0) {
            return random.nextInt(weights.size());
        }
        double target = random.nextDouble() * total;
        double running = 0.0;
        for (int i = 0; i < weights.size(); i++) {
            running += weights.get(i);
            if (target < running) {
                return i;
            }
        }
        return weights.size() - 1;
    }
}
