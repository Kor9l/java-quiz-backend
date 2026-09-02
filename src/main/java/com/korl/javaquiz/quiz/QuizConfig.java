package com.korl.javaquiz.quiz;

import com.korl.javaquiz.domain.LearningModule;
import com.korl.javaquiz.domain.Level;

import java.util.ArrayList;
import java.util.List;

public class QuizConfig {

    private List<String> topicIds = new ArrayList<>();
    private String sectionId;
    // Absent from session payloads written before the module split; the field default is what
    // makes those rounds backend ones, the same way the level field default made pre-V7
    // payloads middle.
    private LearningModule module = LearningModule.BACKEND;
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

    public LearningModule getModule() {
        return module;
    }

    /**
     * Setting the module re-resolves the level, so the two cannot end up describing different
     * ladders whichever order they arrive in — a round configured {@code ENGLISH} while still
     * holding {@code MIDDLE} would query a pool that cannot match and come out empty.
     */
    public void setModule(LearningModule module) {
        this.module = module == null ? LearningModule.BACKEND : module;
        this.level = Level.orDefault(this.module, this.level);
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = Level.orDefault(module, level);
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
