package com.korl.javaquiz.english;

import java.util.ArrayList;
import java.util.List;

/** What one round was set up to be. Fixed when it starts and kept with the session. */
public class WordQuizConfig {

    private List<String> groupIds = new ArrayList<>();
    private TranslationDirection direction = TranslationDirection.EN_RU;
    private int targetCount;
    private boolean infinite;
    private boolean favoritesOnly;
    private boolean smartSelection = true;

    public List<String> getGroupIds() {
        return groupIds;
    }

    public void setGroupIds(List<String> groupIds) {
        this.groupIds = groupIds == null ? new ArrayList<>() : new ArrayList<>(groupIds);
    }

    public TranslationDirection getDirection() {
        return direction;
    }

    public void setDirection(TranslationDirection direction) {
        this.direction = TranslationDirection.orEnRu(direction);
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

    public boolean isFavoritesOnly() {
        return favoritesOnly;
    }

    public void setFavoritesOnly(boolean favoritesOnly) {
        this.favoritesOnly = favoritesOnly;
    }

    public boolean isSmartSelection() {
        return smartSelection;
    }

    public void setSmartSelection(boolean smartSelection) {
        this.smartSelection = smartSelection;
    }
}
