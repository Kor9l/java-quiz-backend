package com.korl.javaquiz.service;

import com.korl.javaquiz.api.dto.LocalizedTextDto;
import com.korl.javaquiz.domain.LearningModule;
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
 *
 * <p>English has a second choice inside it — words or grammar — and it arrives in the same
 * response, as {@code sections}. One call rather than a second endpoint, because this is already
 * the "what can I pick" call and the counts each half is described by are already being read
 * here.
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
        backendCounts.put("topics", topics.findByModuleOrderBySortOrderAsc(LearningModule.BACKEND).size());
        backendCounts.put("questions", questions.countByModule(LearningModule.BACKEND));

        Map<String, Object> wordCounts = new LinkedHashMap<>();
        wordCounts.put("groups", wordGroups.countAccessible(userId));
        wordCounts.put("words", words.countAccessible(userId));

        Map<String, Object> grammarCounts = new LinkedHashMap<>();
        grammarCounts.put("courses", topics.findByModuleOrderBySortOrderAsc(LearningModule.ENGLISH).size());
        grammarCounts.put("questions", questions.countByModule(LearningModule.ENGLISH));

        return List.of(
                // Navigated straight by topics and practice, so there is nothing to choose
                // between here yet. An empty list rather than a missing key, so a client can
                // read `sections` on any module without checking whether it is there.
                module("backend", 0, LocalizedTextDto.of("Backend", "Бэкэнд"),
                        backendCounts, List.of()),
                // The module's own counts stay the words numbers they have always been — that
                // is what the tile showed before grammar existed, and the per-section counts
                // below are where the full picture lives.
                module("english", 1, LocalizedTextDto.of("English", "Английский"),
                        wordCounts,
                        List.of(
                                section("words", 0,
                                        LocalizedTextDto.of("Words", "Слова"), wordCounts),
                                section("grammar", 1,
                                        LocalizedTextDto.of("Grammar", "Грамматика"), grammarCounts))));
    }

    private static Map<String, Object> module(String id, int order, LocalizedTextDto name,
                                              Map<String, Object> counts,
                                              List<Map<String, Object>> sections) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", id);
        dto.put("order", order);
        dto.put("name", name);
        dto.put("counts", counts);
        dto.put("sections", sections);
        return dto;
    }

    private static Map<String, Object> section(String id, int order, LocalizedTextDto name,
                                               Map<String, Object> counts) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", id);
        dto.put("order", order);
        dto.put("name", name);
        dto.put("counts", counts);
        return dto;
    }
}
