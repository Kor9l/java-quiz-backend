package com.korl.javaquiz.service;

import com.korl.javaquiz.api.dto.LocalizedTextDto;
import com.korl.javaquiz.domain.LearningModule;
import com.korl.javaquiz.domain.MaterialSection;
import com.korl.javaquiz.domain.MaterialSectionRepository;
import com.korl.javaquiz.domain.MaterialSource;
import com.korl.javaquiz.domain.PracticeTaskRepository;
import com.korl.javaquiz.domain.QuestionRepository;
import com.korl.javaquiz.domain.ReadState;
import com.korl.javaquiz.domain.Topic;
import com.korl.javaquiz.domain.TopicRepository;
import com.korl.javaquiz.domain.TopicSection;
import com.korl.javaquiz.domain.TopicSectionRepository;
import com.korl.javaquiz.domain.UserProgressEntity;
import com.korl.javaquiz.domain.UserProgressRepository;
import com.korl.javaquiz.domain.UserStatsEntity;
import com.korl.javaquiz.domain.UserStatsRepository;
import com.korl.javaquiz.api.error.ApiException;
import com.korl.javaquiz.userstate.ProgressPayload;
import com.korl.javaquiz.userstate.StatsPayload;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response.Status;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class ContentService {

    private final TopicRepository topics;
    private final TopicSectionRepository sections;
    private final QuestionRepository questions;
    private final MaterialSectionRepository materials;
    private final PracticeTaskRepository practiceTasks;
    private final UserProgressRepository progress;
    private final UserStatsRepository stats;

    public ContentService(
            TopicRepository topics,
            TopicSectionRepository sections,
            QuestionRepository questions,
            MaterialSectionRepository materials,
            PracticeTaskRepository practiceTasks,
            UserProgressRepository progress,
            UserStatsRepository stats) {
        this.topics = topics;
        this.sections = sections;
        this.questions = questions;
        this.materials = materials;
        this.practiceTasks = practiceTasks;
        this.progress = progress;
        this.stats = stats;
    }

    /**
     * The topics of one module, with this learner's read state. Scoped rather than filtered by
     * the caller: the two modules are navigated by separate screens, and a screen that had to
     * remember to drop the other module's topics would eventually forget.
     */
    @Transactional
    public List<Map<String, Object>> listTopics(UUID userId, LearningModule module) {
        ProgressPayload progressPayload = progressPayload(userId);
        StatsPayload statsPayload = statsPayload(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Topic topic : topics.findByModuleOrderBySortOrderAsc(module)) {
            List<TopicSection> topicSections = sections.findByIdTopicIdOrderBySortOrderAsc(topic.getId());
            int unread = 0;
            int read = 0;
            int reread = 0;
            List<Map<String, Object>> sectionDtos = new ArrayList<>();
            for (TopicSection section : topicSections) {
                ReadState state = readState(progressPayload, statsPayload, topic.getId(), section.sectionId());
                switch (state) {
                    case UNREAD -> unread++;
                    case READ -> read++;
                    case NEEDS_REREAD -> reread++;
                }
                Map<String, Object> sectionDto = new LinkedHashMap<>();
                sectionDto.put("id", section.sectionId());
                sectionDto.put("order", section.getSortOrder());
                sectionDto.put("title", LocalizedTextDto.of(section.getTitleEn(), section.getTitleRu()));
                // Reported, not filtered: a section above the reader's level is worth labelling,
                // but hiding it would also hide progress they may already have on it.
                sectionDto.put("level", section.getLevel().name());
                // Null for backend sections, which are grouped by their topic already.
                sectionDto.put("area", section.getArea());
                sectionDto.put("readState", state.name());
                sectionDto.put("questionCount", questions.countByTopicIdAndSectionId(topic.getId(), section.sectionId()));
                sectionDtos.add(sectionDto);
            }
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", topic.getId());
            dto.put("module", topic.getModule().name());
            dto.put("order", topic.getSortOrder());
            dto.put("name", LocalizedTextDto.of(topic.getNameEn(), topic.getNameRu()));
            dto.put("questionCount", questions.countByTopicId(topic.getId()));
            dto.put("sectionCount", topicSections.size());
            dto.put("unreadCount", unread);
            dto.put("readCount", read);
            dto.put("rereadCount", reread);
            dto.put("sections", sectionDtos);
            result.add(dto);
        }
        return result;
    }

    @Transactional
    public Map<String, Object> material(UUID userId, String topicId, String sectionId) {
        TopicSection.Id id = new TopicSection.Id(topicId, sectionId);
        MaterialSection material = materials.findWithSourcesById(id)
                .orElseThrow(() -> new ApiException(Status.NOT_FOUND, "Material not found"));
        TopicSection section = sections.findById(id)
                .orElseThrow(() -> new ApiException(Status.NOT_FOUND, "Section not found"));
        ProgressPayload progressPayload = progressPayload(userId);
        StatsPayload statsPayload = statsPayload(userId);
        ReadState state = readState(progressPayload, statsPayload, topicId, sectionId);
        int wrongSince = wrongSinceRead(progressPayload, statsPayload, topicId, sectionId);

        List<Map<String, String>> sources = new ArrayList<>();
        for (MaterialSource source : material.getSources()) {
            sources.add(Map.of("title", source.getTitle(), "url", source.getUrl()));
        }

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("topicId", topicId);
        dto.put("sectionId", sectionId);
        dto.put("title", LocalizedTextDto.of(section.getTitleEn(), section.getTitleRu()));
        dto.put("level", section.getLevel().name());
        dto.put("area", section.getArea());
        dto.put("estimatedMinutes", material.getEstimatedMinutes());
        dto.put("summary", LocalizedTextDto.of(material.getSummaryEn(), material.getSummaryRu()));
        dto.put("body", LocalizedTextDto.of(material.getBodyEn(), material.getBodyRu()));
        dto.put("sources", sources);
        dto.put("readState", state.name());
        dto.put("readAt", progressPayload.readAt(topicId, sectionId));
        dto.put("wrongSinceRead", wrongSince);
        dto.put("questionCount", questions.countByTopicIdAndSectionId(topicId, sectionId));
        // Hands-on exercises drilling this very section, so the article can send the reader
        // straight to them instead of leaving the two halves of the topic unconnected.
        List<String> tracks = practiceTasks.findTracksForSection(topicId, sectionId);
        dto.put("practiceTrack", tracks.isEmpty() ? null : tracks.get(0));
        dto.put("practiceTaskCount", practiceTasks.countByTopicIdAndSectionId(topicId, sectionId));
        return dto;
    }

    public static ReadState readState(ProgressPayload progress, StatsPayload stats, String topicId, String sectionId) {
        if (!progress.isRead(topicId, sectionId)) {
            return ReadState.UNREAD;
        }
        return wrongSinceRead(progress, stats, topicId, sectionId) > 0 ? ReadState.NEEDS_REREAD : ReadState.READ;
    }

    public static int wrongSinceRead(ProgressPayload progress, StatsPayload stats, String topicId, String sectionId) {
        ProgressPayload.SectionProgress sectionProgress = progress.get(topicId, sectionId);
        if (sectionProgress == null || sectionProgress.readAt == null) {
            return 0;
        }
        StatsPayload.SectionCounter counter = stats.sections.get(topicId + "/" + sectionId);
        int wrongNow = counter == null ? 0 : counter.wrong();
        return Math.max(0, wrongNow - sectionProgress.wrongCountAtRead);
    }

    private ProgressPayload progressPayload(UUID userId) {
        return progress.findById(userId).map(UserProgressEntity::getPayload).orElseGet(ProgressPayload::new);
    }

    private StatsPayload statsPayload(UUID userId) {
        return stats.findById(userId).map(UserStatsEntity::getPayload).orElseGet(StatsPayload::new);
    }
}
