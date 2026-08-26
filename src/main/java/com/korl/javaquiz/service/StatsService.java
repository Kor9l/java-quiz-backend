package com.korl.javaquiz.service;

import com.korl.javaquiz.api.dto.LocalizedTextDto;
import com.korl.javaquiz.api.error.ApiException;
import com.korl.javaquiz.domain.QuestionRepository;
import com.korl.javaquiz.domain.Topic;
import com.korl.javaquiz.domain.TopicRepository;
import com.korl.javaquiz.domain.TopicSection;
import com.korl.javaquiz.domain.TopicSectionRepository;
import com.korl.javaquiz.domain.UserStatsEntity;
import com.korl.javaquiz.domain.UserStatsRepository;
import com.korl.javaquiz.userstate.StatsPayload;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class StatsService {

    private static final int WEAK_MIN_ANSWERS = 3;
    private static final int WEAK_LIMIT = 8;
    private static final int RECENT_LIMIT = 12;

    private final UserStatsRepository stats;
    private final TopicRepository topics;
    private final TopicSectionRepository sections;
    private final QuestionRepository questions;

    public StatsService(
            UserStatsRepository stats,
            TopicRepository topics,
            TopicSectionRepository sections,
            QuestionRepository questions) {
        this.stats = stats;
        this.topics = topics;
        this.sections = sections;
        this.questions = questions;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID userId) {
        StatsPayload payload = stats.findById(userId)
                .map(UserStatsEntity::getPayload)
                .orElseGet(StatsPayload::new);
        long bankSize = questions.count();

        Map<String, Object> overall = new LinkedHashMap<>();
        overall.put("totalAnswered", payload.totalAnswered);
        overall.put("totalCorrect", payload.totalCorrect);
        overall.put("accuracy", payload.accuracy());
        overall.put("bestStreak", payload.bestStreak);
        overall.put("totalTimeMillis", payload.totalTimeMillis);
        overall.put("sessionCount", payload.sessions.size());
        overall.put("seenQuestions", payload.questions.size());
        overall.put("bankSize", bankSize);
        overall.put("firstAnswerAt", payload.firstAnswerAt);
        overall.put("lastAnswerAt", payload.lastAnswerAt);

        Map<String, Topic> topicById = new LinkedHashMap<>();
        for (Topic topic : topics.findAllByOrderBySortOrderAsc()) {
            topicById.put(topic.getId(), topic);
        }

        List<Map<String, Object>> byTopic = new ArrayList<>();
        for (Topic topic : topicById.values()) {
            StatsPayload.Counter counter = payload.topics.getOrDefault(topic.getId(), new StatsPayload.Counter());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("topicId", topic.getId());
            row.put("name", LocalizedTextDto.of(topic.getNameEn(), topic.getNameRu()));
            row.put("answered", counter.answered);
            row.put("correct", counter.correct);
            row.put("accuracy", counter.accuracy());
            byTopic.add(row);
        }

        Map<String, TopicSection> sectionByKey = new LinkedHashMap<>();
        for (TopicSection section : sections.findAllByOrderBySortOrderAsc()) {
            sectionByKey.put(section.topicId() + "/" + section.sectionId(), section);
        }

        List<Map<String, Object>> weakest = payload.sections.entrySet().stream()
                .filter(entry -> entry.getValue().answered >= WEAK_MIN_ANSWERS)
                .sorted(Comparator.comparingDouble((Map.Entry<String, StatsPayload.SectionCounter> e) -> e.getValue().accuracy())
                        .thenComparing(e -> -e.getValue().answered))
                .limit(WEAK_LIMIT)
                .map(entry -> weakestRow(entry, topicById, sectionByKey))
                .toList();

        List<StatsPayload.SessionRecord> source = payload.sessions;
        int from = Math.max(0, source.size() - RECENT_LIMIT);
        List<Map<String, Object>> recentDtos = new ArrayList<>();
        for (int i = source.size() - 1; i >= from; i--) {
            StatsPayload.SessionRecord record = source.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("startedAt", record.startedAt);
            row.put("finishedAt", record.finishedAt);
            row.put("durationMillis", record.durationMillis);
            row.put("answered", record.answered);
            row.put("correct", record.correct);
            row.put("accuracy", record.accuracy());
            row.put("infinite", record.infinite);
            row.put("targetCount", record.targetCount);
            row.put("topics", record.topics);
            recentDtos.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overall", overall);
        result.put("byTopic", byTopic);
        result.put("weakest", weakest);
        result.put("recent", recentDtos);
        return result;
    }

    @Transactional
    public void reset(UUID userId) {
        UserStatsEntity entity = stats.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Stats not found"));
        entity.setPayload(new StatsPayload());
        stats.save(entity);
    }

    private Map<String, Object> weakestRow(
            Map.Entry<String, StatsPayload.SectionCounter> entry,
            Map<String, Topic> topicById,
            Map<String, TopicSection> sectionByKey) {
        String key = entry.getKey();
        int slash = key.indexOf('/');
        String topicId = slash < 0 ? key : key.substring(0, slash);
        String sectionId = slash < 0 ? "" : key.substring(slash + 1);
        Topic topic = topicById.get(topicId);
        TopicSection section = sectionByKey.get(key);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", key);
        row.put("topicId", topicId);
        row.put("sectionId", sectionId);
        row.put("topicName", topic == null
                ? LocalizedTextDto.of(topicId, topicId)
                : LocalizedTextDto.of(topic.getNameEn(), topic.getNameRu()));
        row.put("sectionTitle", section == null
                ? LocalizedTextDto.of(key, key)
                : LocalizedTextDto.of(section.getTitleEn(), section.getTitleRu()));
        row.put("answered", entry.getValue().answered);
        row.put("correct", entry.getValue().correct);
        row.put("accuracy", entry.getValue().accuracy());
        return row;
    }
}
