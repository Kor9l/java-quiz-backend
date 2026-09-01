package com.korl.javaquiz.userstate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One learner's answer history in the English module, mirroring {@link StatsPayload} on the
 * backend side. Breaks down by group and by word, where the other breaks down by topic,
 * section, question and difficulty — same idea, different axes.
 */
public class WordStatsPayload {

    public static final int MAX_SESSIONS = 300;

    public int totalAnswered;
    public int totalCorrect;
    public int bestStreak;
    public long totalTimeMillis;
    public Instant firstAnswerAt;
    public Instant lastAnswerAt;
    public Map<String, Counter> groups = new LinkedHashMap<>();
    public Map<String, WordCounter> words = new LinkedHashMap<>();
    /** Keyed by {@link com.korl.javaquiz.english.TranslationDirection}, so the two are comparable. */
    public Map<String, Counter> directions = new LinkedHashMap<>();
    public List<SessionRecord> sessions = new ArrayList<>();

    public static class Counter {
        public int answered;
        public int correct;

        public int wrong() {
            return answered - correct;
        }

        public double accuracy() {
            return answered == 0 ? 0.0 : (double) correct / answered;
        }
    }

    public static class WordCounter extends Counter {
        public Instant lastAnswerAt;
        public boolean lastCorrect;
    }

    public static class SessionRecord {
        public Instant startedAt;
        public Instant finishedAt;
        public long durationMillis;
        public int answered;
        public int correct;
        public boolean infinite;
        public int targetCount;
        public String direction;
        public List<String> groups = new ArrayList<>();

        public double accuracy() {
            return answered == 0 ? 0.0 : (double) correct / answered;
        }
    }

    public double accuracy() {
        return totalAnswered == 0 ? 0.0 : (double) totalCorrect / totalAnswered;
    }

    public Counter group(String groupId) {
        return groups.computeIfAbsent(groupId, key -> new Counter());
    }

    public WordCounter word(String wordId) {
        return words.computeIfAbsent(wordId, key -> new WordCounter());
    }

    public Counter direction(String direction) {
        return directions.computeIfAbsent(direction, key -> new Counter());
    }

    public void record(String wordId, String groupId, String direction, boolean correct,
                       long elapsedMillis, int currentStreak) {
        Instant now = Instant.now();
        totalAnswered++;
        if (correct) {
            totalCorrect++;
        }
        bestStreak = Math.max(bestStreak, currentStreak);
        totalTimeMillis += Math.max(0, elapsedMillis);
        if (firstAnswerAt == null) {
            firstAnswerAt = now;
        }
        lastAnswerAt = now;

        Counter g = group(groupId);
        g.answered++;
        if (correct) {
            g.correct++;
        }

        WordCounter w = word(wordId);
        w.answered++;
        w.lastAnswerAt = now;
        w.lastCorrect = correct;
        if (correct) {
            w.correct++;
        }

        Counter d = direction(direction);
        d.answered++;
        if (correct) {
            d.correct++;
        }
    }

    public void addSession(SessionRecord record) {
        sessions.add(record);
        while (sessions.size() > MAX_SESSIONS) {
            sessions.remove(0);
        }
    }
}
