package com.korl.javaquiz.english;

import com.korl.javaquiz.quiz.QuizStage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Everything a running round needs, stored as the session's JSONB payload.
 *
 * <p>One difference from the backend quiz it mirrors: there the options belong to the question
 * and the state only remembers the order they were shown in. Here the four wrong options are
 * drawn from other words when the question is asked, so the round has to keep the strings
 * themselves — regenerating them on the next request would quietly change the question the
 * learner is looking at.
 */
public class WordQuizSessionState {

    private WordQuizConfig config = new WordQuizConfig();
    private List<String> deck = new ArrayList<>();
    private List<String> poolIds = new ArrayList<>();
    private QuizStage stage = QuizStage.QUESTION_ONLY;
    private String currentWordId;
    private List<String> options = new ArrayList<>();
    private int correctIndex = -1;
    private int selectedIndex = -1;
    private int askedCount;
    private int answeredCount;
    private int correctCount;
    private int streak;
    private int bestStreak;
    private long elapsedMillis;
    private Instant questionShownAt;
    private Instant startedAt;
    /** Words answered wrongly, so the summary can offer them for another look. */
    private Set<String> missedWordIds = new LinkedHashSet<>();

    public WordQuizConfig getConfig() {
        return config;
    }

    public void setConfig(WordQuizConfig config) {
        this.config = config;
    }

    public List<String> getDeck() {
        return deck;
    }

    public void setDeck(List<String> deck) {
        this.deck = deck;
    }

    public List<String> getPoolIds() {
        return poolIds;
    }

    public void setPoolIds(List<String> poolIds) {
        this.poolIds = poolIds;
    }

    public QuizStage getStage() {
        return stage;
    }

    public void setStage(QuizStage stage) {
        this.stage = stage;
    }

    public String getCurrentWordId() {
        return currentWordId;
    }

    public void setCurrentWordId(String currentWordId) {
        this.currentWordId = currentWordId;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }

    public int getCorrectIndex() {
        return correctIndex;
    }

    public void setCorrectIndex(int correctIndex) {
        this.correctIndex = correctIndex;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int selectedIndex) {
        this.selectedIndex = selectedIndex;
    }

    public int getAskedCount() {
        return askedCount;
    }

    public void setAskedCount(int askedCount) {
        this.askedCount = askedCount;
    }

    public int getAnsweredCount() {
        return answeredCount;
    }

    public void setAnsweredCount(int answeredCount) {
        this.answeredCount = answeredCount;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public void setCorrectCount(int correctCount) {
        this.correctCount = correctCount;
    }

    public int getStreak() {
        return streak;
    }

    public void setStreak(int streak) {
        this.streak = streak;
    }

    public int getBestStreak() {
        return bestStreak;
    }

    public void setBestStreak(int bestStreak) {
        this.bestStreak = bestStreak;
    }

    public long getElapsedMillis() {
        return elapsedMillis;
    }

    public void setElapsedMillis(long elapsedMillis) {
        this.elapsedMillis = elapsedMillis;
    }

    public Instant getQuestionShownAt() {
        return questionShownAt;
    }

    public void setQuestionShownAt(Instant questionShownAt) {
        this.questionShownAt = questionShownAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Set<String> getMissedWordIds() {
        return missedWordIds;
    }

    public void setMissedWordIds(Set<String> missedWordIds) {
        this.missedWordIds = missedWordIds;
    }

    public int targetCount() {
        return config.isInfinite() ? 0 : Math.min(config.getTargetCount(), poolIds.size());
    }

    public double accuracy() {
        return answeredCount == 0 ? 0.0 : (double) correctCount / answeredCount;
    }
}
