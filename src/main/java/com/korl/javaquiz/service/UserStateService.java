package com.korl.javaquiz.service;

import com.korl.javaquiz.api.dto.SettingsRequest;
import com.korl.javaquiz.api.error.ApiException;
import com.korl.javaquiz.domain.TopicSection;
import com.korl.javaquiz.domain.TopicSectionRepository;
import com.korl.javaquiz.domain.UserProgressEntity;
import com.korl.javaquiz.domain.UserProgressRepository;
import com.korl.javaquiz.domain.UserSettingsEntity;
import com.korl.javaquiz.domain.UserSettingsRepository;
import com.korl.javaquiz.domain.UserStatsEntity;
import com.korl.javaquiz.domain.UserStatsRepository;
import com.korl.javaquiz.userstate.ProgressPayload;
import com.korl.javaquiz.userstate.SettingsPayload;
import com.korl.javaquiz.userstate.StatsPayload;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserStateService {

    private final UserSettingsRepository settings;
    private final UserProgressRepository progress;
    private final UserStatsRepository stats;
    private final TopicSectionRepository sections;

    public UserStateService(
            UserSettingsRepository settings,
            UserProgressRepository progress,
            UserStatsRepository stats,
            TopicSectionRepository sections) {
        this.settings = settings;
        this.progress = progress;
        this.stats = stats;
        this.sections = sections;
    }

    @Transactional(readOnly = true)
    public SettingsPayload getSettings(UUID userId) {
        return settings.findById(userId)
                .map(UserSettingsEntity::getPayload)
                .orElseGet(SettingsPayload::new);
    }

    @Transactional
    public SettingsPayload saveSettings(UUID userId, SettingsRequest incoming) {
        UserSettingsEntity entity = settings.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Settings not found"));
        SettingsPayload payload = entity.getPayload() == null ? new SettingsPayload() : entity.getPayload();
        if (incoming.language != null) {
            payload.language = incoming.language;
        }
        if (incoming.selectedTopics != null) {
            payload.setSelectedTopics(incoming.selectedTopics);
        }
        if (incoming.questionCount != null) {
            payload.questionCount = Math.max(SettingsPayload.MIN_COUNT,
                    Math.min(SettingsPayload.MAX_COUNT, incoming.questionCount));
        }
        if (incoming.infiniteMode != null) {
            payload.infiniteMode = incoming.infiniteMode;
        }
        if (incoming.shuffleOptions != null) {
            payload.shuffleOptions = incoming.shuffleOptions;
        }
        if (incoming.smartSelection != null) {
            payload.smartSelection = incoming.smartSelection;
        }
        if (incoming.showExplanation != null) {
            payload.showExplanation = incoming.showExplanation;
        }
        if (incoming.darkTheme != null) {
            payload.darkTheme = incoming.darkTheme;
        }
        entity.setPayload(payload);
        settings.save(entity);
        return payload;
    }

    @Transactional
    public void markRead(UUID userId, String topicId, String sectionId) {
        sections.findById(new TopicSection.Id(topicId, sectionId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Section not found"));
        UserProgressEntity entity = progress.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Progress not found"));
        ProgressPayload payload = entity.getPayload() == null ? new ProgressPayload() : entity.getPayload();
        StatsPayload statsPayload = stats.findById(userId).map(UserStatsEntity::getPayload).orElseGet(StatsPayload::new);
        StatsPayload.SectionCounter counter = statsPayload.sections.get(topicId + "/" + sectionId);
        int wrongNow = counter == null ? 0 : counter.wrong();
        payload.markRead(topicId, sectionId, Instant.now(), wrongNow);
        entity.setPayload(payload);
        progress.save(entity);
    }

    @Transactional
    public void markUnread(UUID userId, String topicId, String sectionId) {
        UserProgressEntity entity = progress.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Progress not found"));
        ProgressPayload payload = entity.getPayload() == null ? new ProgressPayload() : entity.getPayload();
        payload.markUnread(topicId, sectionId);
        entity.setPayload(payload);
        progress.save(entity);
    }

    @Transactional
    public void resetProgress(UUID userId) {
        UserProgressEntity entity = progress.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Progress not found"));
        entity.setPayload(new ProgressPayload());
        progress.save(entity);
    }
}
