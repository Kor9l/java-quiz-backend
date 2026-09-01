package com.korl.javaquiz.service;

import com.korl.javaquiz.domain.Word;
import com.korl.javaquiz.domain.WordGroup;
import com.korl.javaquiz.domain.WordGroupRepository;
import com.korl.javaquiz.domain.WordRepository;
import com.korl.javaquiz.domain.WordStatsEntity;
import com.korl.javaquiz.domain.WordStatsRepository;
import com.korl.javaquiz.userstate.WordStatsPayload;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The English module's statistics, laid out like the backend ones but keyed by group and word. */
@ApplicationScoped
public class WordStatsService {

    private static final int WEAK_MIN_ANSWERS = 2;
    private static final int WEAK_LIMIT = 12;
    private static final int RECENT_LIMIT = 12;

    private final WordStatsRepository stats;
    private final WordGroupRepository groups;
    private final WordRepository words;

    public WordStatsService(WordStatsRepository stats, WordGroupRepository groups, WordRepository words) {
        this.stats = stats;
        this.groups = groups;
        this.words = words;
    }

    @Transactional
    public Map<String, Object> get(UUID userId) {
        WordStatsPayload payload = stats.findById(userId)
                .map(WordStatsEntity::getPayload)
                .orElseGet(WordStatsPayload::new);

        Map<String, Object> overall = new LinkedHashMap<>();
        overall.put("totalAnswered", payload.totalAnswered);
        overall.put("totalCorrect", payload.totalCorrect);
        overall.put("accuracy", payload.accuracy());
        overall.put("bestStreak", payload.bestStreak);
        overall.put("totalTimeMillis", payload.totalTimeMillis);
        overall.put("sessionCount", payload.sessions.size());
        overall.put("seenWords", payload.words.size());
        overall.put("bankSize", words.countAccessible(userId));
        overall.put("firstAnswerAt", payload.firstAnswerAt);
        overall.put("lastAnswerAt", payload.lastAnswerAt);

        List<Map<String, Object>> byGroup = new ArrayList<>();
        for (WordGroup group : groups.findAccessible(userId)) {
            WordStatsPayload.Counter counter =
                    payload.groups.getOrDefault(group.getId().toString(), new WordStatsPayload.Counter());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("groupId", group.getId());
            row.put("title", group.getTitle());
            row.put("answered", counter.answered);
            row.put("correct", counter.correct);
            row.put("accuracy", counter.accuracy());
            byGroup.add(row);
        }

        List<Map<String, Object>> byDirection = new ArrayList<>();
        for (String direction : List.of("EN_RU", "RU_EN")) {
            WordStatsPayload.Counter counter =
                    payload.directions.getOrDefault(direction, new WordStatsPayload.Counter());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("direction", direction);
            row.put("answered", counter.answered);
            row.put("correct", counter.correct);
            row.put("accuracy", counter.accuracy());
            byDirection.add(row);
        }

        // The words worth another look: answered enough times to mean something, and not yet
        // right every time.
        List<Map<String, Object>> weakest = payload.words.entrySet().stream()
                .filter(entry -> entry.getValue().answered >= WEAK_MIN_ANSWERS)
                .filter(entry -> entry.getValue().accuracy() < 1.0)
                .sorted(Comparator.comparingDouble((Map.Entry<String, WordStatsPayload.WordCounter> e)
                                -> e.getValue().accuracy())
                        .thenComparing(e -> -e.getValue().answered))
                .limit(WEAK_LIMIT)
                .map(this::weakestRow)
                .filter(row -> row != null)
                .toList();

        List<WordStatsPayload.SessionRecord> source = payload.sessions;
        int from = Math.max(0, source.size() - RECENT_LIMIT);
        List<Map<String, Object>> recent = new ArrayList<>();
        for (int i = source.size() - 1; i >= from; i--) {
            WordStatsPayload.SessionRecord record = source.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("startedAt", record.startedAt);
            row.put("finishedAt", record.finishedAt);
            row.put("durationMillis", record.durationMillis);
            row.put("answered", record.answered);
            row.put("correct", record.correct);
            row.put("accuracy", record.accuracy());
            row.put("infinite", record.infinite);
            row.put("targetCount", record.targetCount);
            row.put("direction", record.direction);
            recent.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overall", overall);
        result.put("byGroup", byGroup);
        result.put("byDirection", byDirection);
        result.put("weakest", weakest);
        result.put("recent", recent);
        return result;
    }

    /**
     * A learner who has never answered has no row, and that is already the state a reset aims
     * at — so this succeeds quietly rather than reporting a missing row as an error.
     */
    @Transactional
    public void reset(UUID userId) {
        stats.findById(userId).ifPresent(entity -> {
            entity.setPayload(new WordStatsPayload());
            stats.save(entity);
        });
    }

    /** Null for a word that has since been deleted; the history stays, the row is just dropped. */
    private Map<String, Object> weakestRow(Map.Entry<String, WordStatsPayload.WordCounter> entry) {
        Word word = words.findById(UUID.fromString(entry.getKey())).orElse(null);
        if (word == null) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("wordId", word.getId());
        row.put("text", word.getText());
        row.put("translation", word.getTranslation());
        row.put("answered", entry.getValue().answered);
        row.put("correct", entry.getValue().correct);
        row.put("accuracy", entry.getValue().accuracy());
        return row;
    }
}
