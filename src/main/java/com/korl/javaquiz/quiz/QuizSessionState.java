package com.korl.javaquiz.quiz;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class QuizSessionState {

    private QuizConfig config = new QuizConfig();
    private List<String> deck = new ArrayList<>();
    private List<String> poolIds = new ArrayList<>();
    private QuizStage stage = QuizStage.QUESTION_ONLY;
    private String currentQuestionId;
    private List<Integer> displayOptionIndexes = new ArrayList<>();
    private int selectedIndex = -1;
    private int askedCount;
    private int answeredCount;
    private int correctCount;
    private int streak;
    private int bestStreak;
    private long elapsedMillis;
    private Instant questionShownAt;
    private Instant startedAt;
    private Set<String> weakSectionKeys = new LinkedHashSet<>();
    private boolean showExplanation = true;

    public QuizConfig getConfig() {
        return config;
    }

    public void setConfig(QuizConfig config) {
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

    public String getCurrentQuestionId() {
        return currentQuestionId;
    }

    public void setCurrentQuestionId(String currentQuestionId) {
        this.currentQuestionId = currentQuestionId;
    }

    public List<Integer> getDisplayOptionIndexes() {
        return displayOptionIndexes;
    }

    public void setDisplayOptionIndexes(List<Integer> displayOptionIndexes) {
        this.displayOptionIndexes = displayOptionIndexes;
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

    public Set<String> getWeakSectionKeys() {
        return weakSectionKeys;
    }

    public void setWeakSectionKeys(Set<String> weakSectionKeys) {
        this.weakSectionKeys = weakSectionKeys;
    }

    public boolean isShowExplanation() {
        return showExplanation;
    }

    public void setShowExplanation(boolean showExplanation) {
        this.showExplanation = showExplanation;
    }

    public int targetCount() {
        return config.isInfinite() ? 0 : Math.min(config.getTargetCount(), poolIds.size());
    }

    public double accuracy() {
        return answeredCount == 0 ? 0.0 : (double) correctCount / answeredCount;
    }
}
