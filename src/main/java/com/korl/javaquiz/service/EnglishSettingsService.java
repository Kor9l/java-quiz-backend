package com.korl.javaquiz.service;

import com.korl.javaquiz.domain.UserSettingsEntity;
import com.korl.javaquiz.domain.UserSettingsRepository;
import com.korl.javaquiz.domain.WordGroup;
import com.korl.javaquiz.domain.WordGroupRepository;
import com.korl.javaquiz.english.TranslationDirection;
import com.korl.javaquiz.english.WordQuizConfig;
import com.korl.javaquiz.userstate.SettingsPayload;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The English quiz's own setup, held in the shared settings row.
 *
 * <p>Nothing here reaches the settings screen — that is down to the interface language and the
 * theme. These are chosen on the step between pressing start and the first question, and the
 * round that uses them writes them back, so the step opens on what was chosen last time.
 */
@ApplicationScoped
public class EnglishSettingsService {

    private static final int MIN_COUNT = 1;
    private static final int MAX_COUNT = 500;

    private final UserSettingsRepository settings;
    private final WordGroupRepository groups;

    public EnglishSettingsService(UserSettingsRepository settings, WordGroupRepository groups) {
        this.settings = settings;
        this.groups = groups;
    }

    /** What the setup step should open on: the saved choice, minus groups that are gone. */
    @Transactional
    public WordQuizConfig savedSetup(UUID userId) {
        return toConfig(userId, payload(userId));
    }

    /** The saved setup with whatever this request overrode applied on top. */
    @Transactional
    public WordQuizConfig resolveQuizConfig(UUID userId, WordQuizService.WordQuizStartRequest request) {
        SettingsPayload payload = payload(userId);
        WordQuizConfig config = toConfig(userId, payload);
        if (request == null) {
            return config;
        }
        if (request.groupIds != null) {
            config.setGroupIds(keepAccessible(userId, request.groupIds));
        }
        if (request.targetCount != null) {
            config.setTargetCount(clamp(request.targetCount));
        }
        if (request.infinite != null) {
            config.setInfinite(request.infinite);
        }
        if (request.direction != null) {
            config.setDirection(request.direction);
        }
        if (request.favoritesOnly != null) {
            config.setFavoritesOnly(request.favoritesOnly);
        }
        if (request.smartSelection != null) {
            config.setSmartSelection(request.smartSelection);
        }
        return config;
    }

    @Transactional
    public void rememberQuizSetup(UUID userId, WordQuizConfig config) {
        UserSettingsEntity entity = settings.findById(userId).orElse(null);
        if (entity == null) {
            return;
        }
        SettingsPayload payload = entity.getPayload() == null ? new SettingsPayload() : entity.getPayload();
        payload.selectedWordGroups = new ArrayList<>(config.getGroupIds());
        payload.wordQuestionCount = config.getTargetCount();
        payload.wordInfiniteMode = config.isInfinite();
        payload.wordFavoritesOnly = config.isFavoritesOnly();
        payload.wordDirection = config.getDirection();
        entity.setPayload(payload);
        settings.save(entity);
    }

    private SettingsPayload payload(UUID userId) {
        return settings.findById(userId).map(UserSettingsEntity::getPayload).orElseGet(SettingsPayload::new);
    }

    private WordQuizConfig toConfig(UUID userId, SettingsPayload payload) {
        WordQuizConfig config = new WordQuizConfig();
        config.setGroupIds(keepAccessible(userId, payload.selectedWordGroups));
        config.setTargetCount(clamp(payload.wordQuestionCount));
        config.setInfinite(payload.wordInfiniteMode);
        config.setFavoritesOnly(payload.wordFavoritesOnly);
        config.setDirection(TranslationDirection.orEnRu(payload.wordDirection));
        return config;
    }

    /**
     * Drops ids the learner can no longer reach — a personal group they deleted, or one that
     * stopped being shared. An empty result means every accessible group, which is also what an
     * empty selection means, so a stale saved choice degrades to "all" rather than to nothing.
     */
    private List<String> keepAccessible(UUID userId, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<String> accessible = new LinkedHashSet<>();
        for (WordGroup group : groups.findAccessible(userId)) {
            accessible.add(group.getId().toString());
        }
        List<String> kept = new ArrayList<>();
        for (String id : new LinkedHashSet<>(ids)) {
            if (accessible.contains(id)) {
                kept.add(id);
            }
        }
        return kept;
    }

    private static int clamp(int count) {
        return Math.max(MIN_COUNT, Math.min(MAX_COUNT, count));
    }
}
