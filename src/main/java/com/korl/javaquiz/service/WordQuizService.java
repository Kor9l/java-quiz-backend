package com.korl.javaquiz.service;

import com.korl.javaquiz.api.error.ApiException;
import com.korl.javaquiz.domain.Word;
import com.korl.javaquiz.domain.WordFavoriteRepository;
import com.korl.javaquiz.domain.WordGroup;
import com.korl.javaquiz.domain.WordGroupRepository;
import com.korl.javaquiz.domain.WordQuizSessionEntity;
import com.korl.javaquiz.domain.WordQuizSessionRepository;
import com.korl.javaquiz.domain.WordRepository;
import com.korl.javaquiz.domain.WordStatsEntity;
import com.korl.javaquiz.domain.WordStatsRepository;
import com.korl.javaquiz.english.TranslationDirection;
import com.korl.javaquiz.english.WordPicker;
import com.korl.javaquiz.english.WordQuizConfig;
import com.korl.javaquiz.english.WordQuizSessionState;
import com.korl.javaquiz.quiz.QuizStage;
import com.korl.javaquiz.userstate.WordStatsPayload;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response.Status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The English drilling loop, staged like the backend quiz: the word alone first, then the five
 * options, then the verdict. Recalling a translation before seeing the choices is most of the
 * exercise — revealing them straight away turns it into recognition.
 */
@ApplicationScoped
public class WordQuizService {

    private static final int OPTION_COUNT = 5;

    private final WordRepository words;
    private final WordGroupRepository groups;
    private final WordFavoriteRepository favorites;
    private final WordQuizSessionRepository sessions;
    private final WordStatsRepository stats;
    private final EnglishSettingsService settings;
    private final Random random = new Random();

    public WordQuizService(
            WordRepository words,
            WordGroupRepository groups,
            WordFavoriteRepository favorites,
            WordQuizSessionRepository sessions,
            WordStatsRepository stats,
            EnglishSettingsService settings) {
        this.words = words;
        this.groups = groups;
        this.favorites = favorites;
        this.sessions = sessions;
        this.stats = stats;
        this.settings = settings;
    }

    @Transactional
    public Map<String, Object> start(UUID userId, WordQuizStartRequest request) {
        sessions.findActive(userId).ifPresent(this::finishQuietly);

        WordQuizConfig config = resolveConfig(userId, request);
        // "Choose and it is remembered": the setup step is the only place these are picked, so
        // the round that uses them is also what saves them for next time.
        settings.rememberQuizSetup(userId, config);

        List<Word> pool = loadPool(userId, config);
        WordQuizSessionState state = new WordQuizSessionState();
        state.setConfig(config);
        state.setPoolIds(pool.stream().map(word -> word.getId().toString()).toList());
        state.setStartedAt(Instant.now());

        WordStatsPayload statsPayload = statsPayload(userId);
        WordPicker picker = new WordPicker(random);
        refillDeck(state, pool, picker, statsPayload, null);
        nextQuestion(userId, state, pool, picker, statsPayload);

        WordQuizSessionEntity entity = new WordQuizSessionEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setStartedAt(state.getStartedAt());
        applyState(entity, state);
        sessions.save(entity);
        return toView(entity, currentWord(state, pool));
    }

    @Transactional
    public Map<String, Object> current(UUID userId) {
        WordQuizSessionEntity entity = sessions.findActive(userId)
                .orElseThrow(() -> new ApiException(Status.NOT_FOUND, "No active quiz"));
        return toView(entity, loadWord(entity.getPayload().getCurrentWordId()));
    }

    @Transactional
    public Map<String, Object> reveal(UUID userId, UUID sessionId) {
        WordQuizSessionEntity entity = loadOwned(userId, sessionId);
        WordQuizSessionState state = entity.getPayload();
        if (state.getStage() != QuizStage.QUESTION_ONLY || state.getCurrentWordId() == null) {
            throw new ApiException(Status.CONFLICT, "Cannot reveal answers now");
        }
        state.setStage(QuizStage.OPTIONS_REVEALED);
        applyState(entity, state);
        sessions.save(entity);
        return toView(entity, loadWord(state.getCurrentWordId()));
    }

    @Transactional
    public Map<String, Object> answer(UUID userId, UUID sessionId, int optionIndex) {
        WordQuizSessionEntity entity = loadOwned(userId, sessionId);
        WordQuizSessionState state = entity.getPayload();
        if (state.getStage() != QuizStage.OPTIONS_REVEALED) {
            throw new ApiException(Status.CONFLICT, "Cannot answer now");
        }
        if (optionIndex < 0 || optionIndex >= state.getOptions().size()) {
            throw new ApiException(Status.BAD_REQUEST, "Invalid option");
        }
        Word word = loadWord(state.getCurrentWordId());
        if (word == null) {
            throw new ApiException(Status.CONFLICT, "The word being asked is gone");
        }

        long elapsed = 0;
        if (state.getQuestionShownAt() != null) {
            elapsed = Math.max(0, Duration.between(state.getQuestionShownAt(), Instant.now()).toMillis());
        }
        boolean correct = optionIndex == state.getCorrectIndex();
        state.setSelectedIndex(optionIndex);
        state.setStage(QuizStage.ANSWERED);
        state.setAnsweredCount(state.getAnsweredCount() + 1);
        state.setElapsedMillis(state.getElapsedMillis() + elapsed);
        if (correct) {
            state.setCorrectCount(state.getCorrectCount() + 1);
            state.setStreak(state.getStreak() + 1);
            state.setBestStreak(Math.max(state.getBestStreak(), state.getStreak()));
        } else {
            state.setStreak(0);
            state.getMissedWordIds().add(word.getId().toString());
        }

        WordStatsEntity statsEntity = statsEntity(userId);
        WordStatsPayload payload = statsEntity.getPayload();
        payload.record(word.getId().toString(), word.getGroupId().toString(),
                state.getConfig().getDirection().name(), correct, elapsed, state.getStreak());
        statsEntity.setPayload(payload);
        stats.save(statsEntity);

        applyState(entity, state);
        sessions.save(entity);
        return toView(entity, word);
    }

    @Transactional
    public Map<String, Object> advance(UUID userId, UUID sessionId) {
        WordQuizSessionEntity entity = loadOwned(userId, sessionId);
        WordQuizSessionState state = entity.getPayload();
        if (state.getStage() != QuizStage.ANSWERED) {
            throw new ApiException(Status.CONFLICT, "Cannot advance now");
        }
        if (!state.getConfig().isInfinite() && state.getAskedCount() >= state.targetCount()) {
            finish(entity, state, userId);
            return toView(entity, null);
        }
        WordStatsPayload statsPayload = statsPayload(userId);
        List<Word> pool = loadPool(userId, state.getConfig());
        nextQuestion(userId, state, pool, new WordPicker(random), statsPayload);
        applyState(entity, state);
        sessions.save(entity);
        return toView(entity, loadWord(state.getCurrentWordId()));
    }

    @Transactional
    public Map<String, Object> quit(UUID userId, UUID sessionId) {
        WordQuizSessionEntity entity = loadOwned(userId, sessionId);
        finish(entity, entity.getPayload(), userId);
        return toView(entity, null);
    }

    private void finishQuietly(WordQuizSessionEntity entity) {
        WordQuizSessionState state = entity.getPayload();
        if (state.getStage() == QuizStage.FINISHED) {
            return;
        }
        finish(entity, state, entity.getUserId());
    }

    private void finish(WordQuizSessionEntity entity, WordQuizSessionState state, UUID userId) {
        if (state.getStage() == QuizStage.FINISHED) {
            applyState(entity, state);
            sessions.save(entity);
            return;
        }
        state.setStage(QuizStage.FINISHED);
        entity.setFinished(true);
        entity.setFinishedAt(Instant.now());
        if (state.getAnsweredCount() > 0) {
            WordStatsEntity statsEntity = statsEntity(userId);
            WordStatsPayload payload = statsEntity.getPayload();
            WordStatsPayload.SessionRecord record = new WordStatsPayload.SessionRecord();
            record.startedAt = state.getStartedAt();
            record.finishedAt = entity.getFinishedAt();
            record.durationMillis = state.getElapsedMillis();
            record.answered = state.getAnsweredCount();
            record.correct = state.getCorrectCount();
            record.infinite = state.getConfig().isInfinite();
            record.targetCount = state.targetCount();
            record.direction = state.getConfig().getDirection().name();
            record.groups = new ArrayList<>(state.getConfig().getGroupIds());
            payload.addSession(record);
            statsEntity.setPayload(payload);
            stats.save(statsEntity);
        }
        applyState(entity, state);
        sessions.save(entity);
    }

    private WordStatsEntity statsEntity(UUID userId) {
        WordStatsEntity entity = stats.findById(userId).orElseGet(() -> {
            WordStatsEntity created = new WordStatsEntity();
            created.setUserId(userId);
            created.setPayload(new WordStatsPayload());
            return created;
        });
        if (entity.getPayload() == null) {
            entity.setPayload(new WordStatsPayload());
        }
        return entity;
    }

    private WordStatsPayload statsPayload(UUID userId) {
        return stats.findById(userId).map(WordStatsEntity::getPayload).orElseGet(WordStatsPayload::new);
    }

    private void nextQuestion(UUID userId, WordQuizSessionState state, List<Word> pool,
                              WordPicker picker, WordStatsPayload statsPayload) {
        if (state.getDeck().isEmpty()) {
            refillDeck(state, pool, picker, statsPayload, state.getCurrentWordId());
        }
        if (state.getDeck().isEmpty()) {
            state.setStage(QuizStage.FINISHED);
            state.setCurrentWordId(null);
            return;
        }
        String nextId = state.getDeck().remove(0);
        Word next = pool.stream().filter(word -> word.getId().toString().equals(nextId)).findFirst().orElse(null);
        if (next == null) {
            state.setStage(QuizStage.FINISHED);
            state.setCurrentWordId(null);
            return;
        }
        state.setCurrentWordId(next.getId().toString());
        state.setAskedCount(state.getAskedCount() + 1);
        state.setStage(QuizStage.QUESTION_ONLY);
        state.setSelectedIndex(-1);
        buildOptions(userId, state, next);
        state.setQuestionShownAt(Instant.now());
    }

    /**
     * The right answer plus four others, drawn from everything the learner can see rather than
     * from the round's groups: a five-word group would otherwise give the answer away by
     * running out of plausible alternatives.
     */
    private void buildOptions(UUID userId, WordQuizSessionState state, Word word) {
        boolean enToRu = state.getConfig().getDirection() == TranslationDirection.EN_RU;
        String answer = enToRu ? word.getTranslation() : word.getText();
        List<String> candidates = enToRu
                ? words.findAccessibleTranslations(userId)
                : words.findAccessibleTexts(userId);

        // A set, because two words can share a translation and an option list that repeats the
        // right answer has no right answer.
        Set<String> distinct = new LinkedHashSet<>(candidates);
        distinct.remove(answer);
        List<String> pool = new ArrayList<>(distinct);
        Collections.shuffle(pool, random);

        List<String> options = new ArrayList<>();
        options.add(answer);
        for (String candidate : pool) {
            if (options.size() >= OPTION_COUNT) {
                break;
            }
            options.add(candidate);
        }
        Collections.shuffle(options, random);

        state.setOptions(options);
        state.setCorrectIndex(options.indexOf(answer));
    }

    private void refillDeck(WordQuizSessionState state, List<Word> pool, WordPicker picker,
                            WordStatsPayload statsPayload, String currentId) {
        if (pool.isEmpty()) {
            return;
        }
        int want = state.getConfig().isInfinite()
                ? pool.size()
                : Math.max(0, state.getConfig().getTargetCount() - state.getAskedCount());
        if (want <= 0) {
            return;
        }
        List<Word> next = picker.pick(pool, want, state.getConfig().isSmartSelection(), statsPayload);
        if (next.isEmpty()) {
            return;
        }
        if (currentId != null && next.size() > 1 && next.get(0).getId().toString().equals(currentId)) {
            Collections.swap(next, 0, 1);
        }
        List<String> deck = new ArrayList<>(state.getDeck());
        for (Word word : next) {
            deck.add(word.getId().toString());
        }
        state.setDeck(deck);
    }

    private WordQuizConfig resolveConfig(UUID userId, WordQuizStartRequest request) {
        return settings.resolveQuizConfig(userId, request);
    }

    private List<Word> loadPool(UUID userId, WordQuizConfig config) {
        List<UUID> groupIds = config.getGroupIds().stream().map(UUID::fromString).toList();
        List<Word> pool = words.findAccessibleInGroups(userId, groupIds);
        if (config.isFavoritesOnly()) {
            Set<UUID> starred = favorites.findWordIds(userId);
            pool = pool.stream().filter(word -> starred.contains(word.getId())).toList();
        }
        return pool;
    }

    private Word currentWord(WordQuizSessionState state, List<Word> pool) {
        if (state.getCurrentWordId() == null) {
            return null;
        }
        return pool.stream()
                .filter(word -> word.getId().toString().equals(state.getCurrentWordId()))
                .findFirst()
                .orElseGet(() -> loadWord(state.getCurrentWordId()));
    }

    private Word loadWord(String id) {
        if (id == null) {
            return null;
        }
        return words.findById(UUID.fromString(id)).orElse(null);
    }

    private WordQuizSessionEntity loadOwned(UUID userId, UUID sessionId) {
        WordQuizSessionEntity entity = sessions.findById(sessionId)
                .orElseThrow(() -> new ApiException(Status.NOT_FOUND, "Quiz session not found"));
        if (!entity.getUserId().equals(userId)) {
            throw new ApiException(Status.FORBIDDEN, "Quiz session not found");
        }
        return entity;
    }

    private void applyState(WordQuizSessionEntity entity, WordQuizSessionState state) {
        entity.setPayload(state);
        entity.setStage(state.getStage().name());
        entity.setFinished(state.getStage() == QuizStage.FINISHED);
        if (entity.isFinished() && entity.getFinishedAt() == null) {
            entity.setFinishedAt(Instant.now());
        }
    }

    private Map<String, Object> toView(WordQuizSessionEntity entity, Word word) {
        WordQuizSessionState state = entity.getPayload();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", entity.getId());
        view.put("stage", state.getStage().name());
        view.put("direction", state.getConfig().getDirection().name());
        view.put("askedCount", state.getAskedCount());
        view.put("answeredCount", state.getAnsweredCount());
        view.put("correctCount", state.getCorrectCount());
        view.put("streak", state.getStreak());
        view.put("bestStreak", state.getBestStreak());
        view.put("elapsedMillis", state.getElapsedMillis());
        view.put("accuracy", state.accuracy());
        view.put("targetCount", state.targetCount());
        view.put("infinite", state.getConfig().isInfinite());
        view.put("empty", state.getPoolIds().isEmpty());
        if (state.getStage() == QuizStage.FINISHED && !state.getMissedWordIds().isEmpty()) {
            view.put("missedWords", missedWords(state.getMissedWordIds()));
        }

        if (word != null && state.getStage() != QuizStage.FINISHED) {
            boolean enToRu = state.getConfig().getDirection() == TranslationDirection.EN_RU;
            Map<String, Object> question = new LinkedHashMap<>();
            question.put("wordId", word.getId());
            question.put("prompt", enToRu ? word.getText() : word.getTranslation());
            if (state.getStage() != QuizStage.QUESTION_ONLY) {
                question.put("options", state.getOptions());
                if (state.getStage() == QuizStage.ANSWERED) {
                    question.put("selectedIndex", state.getSelectedIndex());
                    question.put("correctIndex", state.getCorrectIndex());
                    question.put("text", word.getText());
                    question.put("translation", word.getTranslation());
                    question.put("example", word.getExample());
                }
            }
            view.put("question", question);
        }
        return view;
    }

    /** The words missed in this round, named, so the summary is readable rather than a list of ids. */
    private List<Map<String, Object>> missedWords(Set<String> ids) {
        Map<UUID, WordGroup> groupCache = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String id : ids) {
            Word word = loadWord(id);
            if (word == null) {
                continue;
            }
            WordGroup group = groupCache.computeIfAbsent(word.getGroupId(),
                    key -> groups.findById(key).orElse(null));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", word.getId());
            row.put("text", word.getText());
            row.put("translation", word.getTranslation());
            row.put("example", word.getExample());
            row.put("groupTitle", group == null ? null : group.getTitle());
            rows.add(row);
        }
        return rows;
    }

    /** Body of {@code POST /api/english/quiz/start}: the setup step, all of it optional. */
    public static class WordQuizStartRequest {
        public List<String> groupIds;
        public Integer targetCount;
        public Boolean infinite;
        public TranslationDirection direction;
        public Boolean favoritesOnly;
        public Boolean smartSelection;
    }
}
