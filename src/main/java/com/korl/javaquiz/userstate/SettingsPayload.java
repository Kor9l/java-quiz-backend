package com.korl.javaquiz.userstate;

import com.korl.javaquiz.domain.Language;
import com.korl.javaquiz.domain.Level;
import com.korl.javaquiz.domain.Topic;
import com.korl.javaquiz.english.TranslationDirection;

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
    // Absent from payloads written before V7; the field default is what makes those middle.
    public Level level = Level.MIDDLE;
    public List<String> selectedTopics = new ArrayList<>();
    public int questionCount = 20;
    public boolean infiniteMode;
    public boolean shuffleOptions = true;
    public boolean smartSelection = true;
    public boolean showExplanation = true;
    public boolean darkTheme;

    // The English module's own setup, remembered the same way. Not shown on the settings
    // screen — both quizzes are set up on the step between pressing start and the first
    // question, and that step writes what it was given back here.
    public List<String> selectedWordGroups = new ArrayList<>();
    public int wordQuestionCount = 10;
    public boolean wordInfiniteMode;
    public boolean wordFavoritesOnly;
    public TranslationDirection wordDirection = TranslationDirection.EN_RU;

    public boolean allTopics() {
        return selectedTopics == null || selectedTopics.isEmpty();
    }

    public List<String> effectiveTopics(List<Topic> catalog) {
        return effectiveTopics(catalog, selectedTopics);
    }

    /**
     * The topics a selection actually resolves to, in catalog order. An empty selection means
     * every topic — including ones added after the choice was made, which is why the selection
     * is stored empty rather than expanded to the ids that happened to exist at the time.
     */
    public static List<String> effectiveTopics(List<Topic> catalog, List<String> selection) {
        List<String> all = catalog.stream().map(Topic::getId).toList();
        if (selection == null || selection.isEmpty()) {
            return all;
        }
        Set<String> kept = new LinkedHashSet<>();
        for (String id : all) {
            if (selection.contains(id)) {
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
