package com.korl.javaquiz.userstate;

import com.korl.javaquiz.domain.Language;
import com.korl.javaquiz.domain.Topic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SettingsPayload {

    public static final int[] COUNT_PRESETS = {10, 20, 30, 50, 100};
    public static final int MIN_COUNT = 1;
    public static final int MAX_COUNT = 500;

    public Language language = Language.RU;
    public List<String> selectedTopics = new ArrayList<>();
    public int questionCount = 20;
    public boolean infiniteMode;
    public boolean shuffleOptions = true;
    public boolean smartSelection = true;
    public boolean showExplanation = true;
    public boolean darkTheme;

    public boolean allTopics() {
        return selectedTopics == null || selectedTopics.isEmpty();
    }

    public List<String> effectiveTopics(List<Topic> catalog) {
        List<String> all = catalog.stream().map(Topic::getId).toList();
        if (allTopics()) {
            return all;
        }
        Set<String> kept = new LinkedHashSet<>();
        for (String id : all) {
            if (selectedTopics.contains(id)) {
                kept.add(id);
            }
        }
        return kept.isEmpty() ? all : new ArrayList<>(kept);
    }

    public void setSelectedTopics(Collection<String> ids) {
        selectedTopics = new ArrayList<>(new LinkedHashSet<>(ids));
    }

    public int normalizedQuestionCount() {
        return Math.max(MIN_COUNT, Math.min(MAX_COUNT, questionCount));
    }
}
