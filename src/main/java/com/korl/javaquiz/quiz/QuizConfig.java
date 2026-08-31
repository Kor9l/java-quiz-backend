package com.korl.javaquiz.quiz;

import com.korl.javaquiz.domain.Level;

import java.util.ArrayList;
import java.util.List;

public class QuizConfig {

    private List<String> topicIds = new ArrayList<>();
    private String sectionId;
    private Level level = Level.MIDDLE;
    private int targetCount;
    private boolean infinite;
    private boolean shuffleOptions;
    private boolean smartSelection;

    public List<String> getTopicIds() {
        return topicIds;
    }

    public void setTopicIds(List<String> topicIds) {
        this.topicIds = topicIds == null ? new ArrayList<>() : new ArrayList<>(topicIds);
    }

    public String getSectionId() {
        return sectionId;
    }

    public void setSectionId(String sectionId) {
        this.sectionId = sectionId;
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = Level.orMiddle(level);
    }

    public int getTargetCount() {
        return targetCount;
    }

    public void setTargetCount(int targetCount) {
        this.targetCount = targetCount;
    }

    public boolean isInfinite() {
        return infinite;
    }

    public void setInfinite(boolean infinite) {
        this.infinite = infinite;
    }

    public boolean isShuffleOptions() {
        return shuffleOptions;
    }

    public void setShuffleOptions(boolean shuffleOptions) {
        this.shuffleOptions = shuffleOptions;
    }

    public boolean isSmartSelection() {
        return smartSelection;
    }

    public void setSmartSelection(boolean smartSelection) {
        this.smartSelection = smartSelection;
    }
}
