package com.korl.javaquiz.service;

import com.korl.javaquiz.api.dto.LocalizedTextDto;
import com.korl.javaquiz.domain.QuestionRepository;
import com.korl.javaquiz.domain.TopicRepository;
import com.korl.javaquiz.domain.WordGroupRepository;
import com.korl.javaquiz.domain.WordRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The two things this app now teaches, which is the choice the learner makes right after signing
 * in. Counts rather than sentences: the UI already localises and formats numbers, and duplicating
 * that here would mean two places to keep in step.
 */
@ApplicationScoped
public class ModuleService {

    private final TopicRepository topics;
    private final QuestionRepository questions;
    private final WordGroupRepository wordGroups;
    private final WordRepository words;

    public ModuleService(TopicRepository topics, QuestionRepository questions,
                         WordGroupRepository wordGroups, WordRepository words) {
        this.topics = topics;
        this.questions = questions;
        this.wordGroups = wordGroups;
        this.words = words;
    }

    @Transactional
    public List<Map<String, Object>> list(UUID userId) {
        Map<String, Object> backendCounts = new LinkedHashMap<>();
        backendCounts.put("topics", topics.findAllByOrderBySortOrderAsc().size());
        backendCounts.put("questions", questions.count());

        Map<String, Object> englishCounts = new LinkedHashMap<>();
        englishCounts.put("groups", wordGroups.countAccessible(userId));
        englishCounts.put("words", words.countAccessible(userId));

        return List.of(
                module("backend", 0, LocalizedTextDto.of("Backend", "Бэкэнд"),
                        backendCounts),
                module("english", 1,
                        LocalizedTextDto.of("English", "Английский"),
                        englishCounts));
    }

    private static Map<String, Object> module(String id, int order, LocalizedTextDto name,
                                              Map<String, Object> counts) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", id);
        dto.put("order", order);
        dto.put("name", name);
        dto.put("counts", counts);
        return dto;
    }
}
