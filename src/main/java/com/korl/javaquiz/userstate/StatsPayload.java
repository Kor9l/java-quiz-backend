package com.korl.javaquiz.userstate;

import com.korl.javaquiz.domain.Difficulty;
import com.korl.javaquiz.domain.Question;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StatsPayload {

    public static final int MAX_SESSIONS = 300;

    public int totalAnswered;
    public int totalCorrect;
    public int bestStreak;
    public long totalTimeMillis;
    public Instant firstAnswerAt;
    public Instant lastAnswerAt;
    public Map<String, Counter> topics = new LinkedHashMap<>();
    public Map<String, SectionCounter> sections = new LinkedHashMap<>();
    public Map<String, QuestionCounter> questions = new LinkedHashMap<>();
    public Map<Difficulty, Counter> difficulties = new LinkedHashMap<>();
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

    public static class SectionCounter extends Counter {
        public Instant lastAnswerAt;
        public Instant lastWrongAt;
    }

    public static class QuestionCounter extends Counter {
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
        public List<String> topics = new ArrayList<>();

        public double accuracy() {
            return answered == 0 ? 0.0 : (double) correct / answered;
        }
    }

    public double accuracy() {
        return totalAnswered == 0 ? 0.0 : (double) totalCorrect / totalAnswered;
    }

    public Counter topic(String topicId) {
        return topics.computeIfAbsent(topicId, k -> new Counter());
    }

    public SectionCounter section(String topicId, String sectionId) {
        return sections.computeIfAbsent(topicId + "/" + sectionId, k -> new SectionCounter());
    }

    public QuestionCounter question(String questionId) {
        return questions.computeIfAbsent(questionId, k -> new QuestionCounter());
    }

    public Counter difficulty(Difficulty difficulty) {
        return difficulties.computeIfAbsent(difficulty, k -> new Counter());
    }

    public void record(Question question, boolean correct, long elapsedMillis, int currentStreak) {
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

        Counter t = topic(question.getTopicId());
        t.answered++;
        if (correct) {
            t.correct++;
        }

        SectionCounter s = section(question.getTopicId(), question.getSectionId());
        s.answered++;
        s.lastAnswerAt = now;
        if (correct) {
            s.correct++;
        } else {
            s.lastWrongAt = now;
        }

        QuestionCounter q = question(question.getId());
        q.answered++;
        q.lastAnswerAt = now;
        q.lastCorrect = correct;
        if (correct) {
            q.correct++;
        }

        Counter d = difficulty(question.getDifficulty());
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

    public void reset() {
        totalAnswered = 0;
        totalCorrect = 0;
        bestStreak = 0;
        totalTimeMillis = 0;
        firstAnswerAt = null;
        lastAnswerAt = null;
        topics = new LinkedHashMap<>();
        sections = new LinkedHashMap<>();
        questions = new LinkedHashMap<>();
        difficulties = new LinkedHashMap<>();
        sessions = new ArrayList<>();
    }
}
