package com.korl.javaquiz.quiz;

import com.korl.javaquiz.domain.Question;
import com.korl.javaquiz.userstate.StatsPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class QuestionPicker {

    static final double UNSEEN_WEIGHT = 4.0;
    static final double MASTERED_WEIGHT = 0.35;
    static final double RECENT_MISS_BONUS = 2.0;

    private final Random random;

    public QuestionPicker(Random random) {
        this.random = random;
    }

    public List<Question> pick(List<Question> pool, int count, boolean smart, StatsPayload stats) {
        List<Question> candidates = new ArrayList<>(pool);
        int take = Math.min(count, candidates.size());
        if (take <= 0) {
            return List.of();
        }
        if (!smart || stats == null) {
            java.util.Collections.shuffle(candidates, random);
            return new ArrayList<>(candidates.subList(0, take));
        }

        List<Double> weights = new ArrayList<>(candidates.size());
        for (Question question : candidates) {
            weights.add(weightOf(question, stats));
        }
        List<Question> chosen = new ArrayList<>(take);
        for (int i = 0; i < take; i++) {
            int index = sampleIndex(weights);
            chosen.add(candidates.get(index));
            candidates.remove(index);
            weights.remove(index);
        }
        return chosen;
    }

    double weightOf(Question question, StatsPayload stats) {
        StatsPayload.QuestionCounter counter = stats.questions.get(question.getId());
        if (counter == null || counter.answered == 0) {
            return UNSEEN_WEIGHT * question.getDifficulty().weight();
        }
        double wrongRate = 1.0 - counter.accuracy();
        double weight = MASTERED_WEIGHT + 2.5 * wrongRate;
        if (!counter.lastCorrect) {
            weight += RECENT_MISS_BONUS;
        }
        return weight * question.getDifficulty().weight();
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
