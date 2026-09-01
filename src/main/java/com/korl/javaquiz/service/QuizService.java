package com.korl.javaquiz.service;

import com.korl.javaquiz.api.dto.LocalizedTextDto;
import com.korl.javaquiz.api.error.ApiException;
import com.korl.javaquiz.domain.Level;
import com.korl.javaquiz.domain.Question;
import com.korl.javaquiz.domain.QuestionOption;
import com.korl.javaquiz.domain.QuestionRepository;
import com.korl.javaquiz.domain.QuestionSource;
import com.korl.javaquiz.domain.QuizSessionEntity;
import com.korl.javaquiz.domain.QuizSessionRepository;
import com.korl.javaquiz.domain.Topic;
import com.korl.javaquiz.domain.TopicRepository;
import com.korl.javaquiz.domain.TopicSection;
import com.korl.javaquiz.domain.TopicSectionRepository;
import com.korl.javaquiz.domain.UserSettingsEntity;
import com.korl.javaquiz.domain.UserSettingsRepository;
import com.korl.javaquiz.domain.UserStatsEntity;
import com.korl.javaquiz.domain.UserStatsRepository;
import com.korl.javaquiz.quiz.QuestionPicker;
import com.korl.javaquiz.quiz.QuizConfig;
import com.korl.javaquiz.quiz.QuizSessionState;
import com.korl.javaquiz.quiz.QuizStage;
import com.korl.javaquiz.userstate.SettingsPayload;
import com.korl.javaquiz.userstate.StatsPayload;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response.Status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@ApplicationScoped
public class QuizService {

    private final QuestionRepository questions;
    private final TopicRepository topics;
    private final TopicSectionRepository sections;
    private final QuizSessionRepository sessions;
    private final UserSettingsRepository settings;
    private final UserStatsRepository stats;
    private final Random random = new Random();

    public QuizService(
            QuestionRepository questions,
            TopicRepository topics,
            TopicSectionRepository sections,
            QuizSessionRepository sessions,
            UserSettingsRepository settings,
            UserStatsRepository stats) {
        this.questions = questions;
        this.topics = topics;
        this.sections = sections;
        this.sessions = sessions;
        this.settings = settings;
        this.stats = stats;
    }

    @Transactional
    public Map<String, Object> start(UUID userId, QuizStartRequest request) {
        sessions.findFirstByUserIdAndFinishedFalseOrderByStartedAtDesc(userId).ifPresent(this::finishQuietly);

        SettingsPayload settingsPayload = settings.findById(userId)
                .map(UserSettingsEntity::getPayload)
                .orElseGet(SettingsPayload::new);
        StatsPayload statsPayload = stats.findById(userId)
                .map(UserStatsEntity::getPayload)
                .orElseGet(StatsPayload::new);

        // Kept unexpanded: empty means "every topic", and saving it as the ids that exist today
        // would quietly exclude any topic added later.
        List<String> chosenTopics = request != null && request.topicIds != null
                ? new ArrayList<>(request.topicIds)
                : new ArrayList<>(settingsPayload.selectedTopics);
        QuizConfig config = resolveConfig(request, settingsPayload, chosenTopics);
        boolean showExplanation = request != null && request.showExplanation != null
                ? request.showExplanation
                : settingsPayload.showExplanation;
        rememberSetup(userId, config, chosenTopics, showExplanation);
        List<Question> pool = loadPool(config);
        QuizSessionState state = new QuizSessionState();
        state.setConfig(config);
        state.setPoolIds(pool.stream().map(Question::getId).toList());
        state.setStartedAt(Instant.now());
        state.setShowExplanation(showExplanation);

        QuestionPicker picker = new QuestionPicker(random, config.getLevel());
        refillDeck(state, pool, picker, statsPayload, null);
        nextQuestion(state, pool, picker, statsPayload);

        QuizSessionEntity entity = new QuizSessionEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setStartedAt(state.getStartedAt());
        applyState(entity, state);
        sessions.save(entity);
        return toView(entity, currentQuestion(state, pool));
    }

    @Transactional
    public Map<String, Object> current(UUID userId) {
        QuizSessionEntity entity = sessions.findFirstByUserIdAndFinishedFalseOrderByStartedAtDesc(userId)
                .orElseThrow(() -> new ApiException(Status.NOT_FOUND, "No active quiz"));
        return toView(entity, loadQuestion(entity.getPayload().getCurrentQuestionId()));
    }

    @Transactional
    public Map<String, Object> reveal(UUID userId, UUID sessionId) {
        QuizSessionEntity entity = loadOwned(userId, sessionId);
        QuizSessionState state = entity.getPayload();
        if (state.getStage() != QuizStage.QUESTION_ONLY || state.getCurrentQuestionId() == null) {
            throw new ApiException(Status.CONFLICT, "Cannot reveal answers now");
        }
        state.setStage(QuizStage.OPTIONS_REVEALED);
        applyState(entity, state);
        sessions.save(entity);
        return toView(entity, loadQuestion(state.getCurrentQuestionId()));
    }

    @Transactional
    public Map<String, Object> answer(UUID userId, UUID sessionId, int displayIndex) {
        QuizSessionEntity entity = loadOwned(userId, sessionId);
        QuizSessionState state = entity.getPayload();
        if (state.getStage() != QuizStage.OPTIONS_REVEALED) {
            throw new ApiException(Status.CONFLICT, "Cannot answer now");
        }
        List<Integer> order = state.getDisplayOptionIndexes();
        if (displayIndex < 0 || displayIndex >= order.size()) {
            throw new ApiException(Status.BAD_REQUEST, "Invalid option");
        }
        Question question = loadQuestion(state.getCurrentQuestionId());
        int originalIndex = order.get(displayIndex);
        QuestionOption chosen = question.getOptions().stream()
                .filter(option -> option.getOptionIndex() == originalIndex)
                .findFirst()
                .orElseThrow(() -> new ApiException(Status.BAD_REQUEST, "Invalid option"));

        long elapsed = 0;
        if (state.getQuestionShownAt() != null) {
            elapsed = Math.max(0, Duration.between(state.getQuestionShownAt(), Instant.now()).toMillis());
        }
        boolean correct = chosen.isCorrect();
        state.setSelectedIndex(displayIndex);
        state.setStage(QuizStage.ANSWERED);
        state.setAnsweredCount(state.getAnsweredCount() + 1);
        state.setElapsedMillis(state.getElapsedMillis() + elapsed);
        if (correct) {
            state.setCorrectCount(state.getCorrectCount() + 1);
            state.setStreak(state.getStreak() + 1);
            state.setBestStreak(Math.max(state.getBestStreak(), state.getStreak()));
        } else {
            state.setStreak(0);
            state.getWeakSectionKeys().add(question.sectionKey());
        }

        UserStatsEntity statsEntity = statsEntity(userId);
        StatsPayload payload = statsEntity.getPayload();
        payload.record(question, correct, elapsed, state.getStreak());
        statsEntity.setPayload(payload);
        stats.save(statsEntity);

        applyState(entity, state);
        sessions.save(entity);
        return toView(entity, question);
    }

    @Transactional
    public Map<String, Object> advance(UUID userId, UUID sessionId) {
        QuizSessionEntity entity = loadOwned(userId, sessionId);
        QuizSessionState state = entity.getPayload();
        if (state.getStage() != QuizStage.ANSWERED) {
            throw new ApiException(Status.CONFLICT, "Cannot advance now");
        }
        if (!state.getConfig().isInfinite() && state.getAskedCount() >= state.targetCount()) {
            finish(entity, state, userId);
            return toView(entity, null);
        }
        StatsPayload statsPayload = stats.findById(userId).map(UserStatsEntity::getPayload).orElseGet(StatsPayload::new);
        List<Question> pool = loadPool(state.getConfig());
        QuestionPicker picker = new QuestionPicker(random, state.getConfig().getLevel());
        // nextQuestion refills with the current id, which already keeps a fresh deck from
        // repeating the question just answered; asking twice here would double askedCount.
        nextQuestion(state, pool, picker, statsPayload);
        applyState(entity, state);
        sessions.save(entity);
        return toView(entity, loadQuestion(state.getCurrentQuestionId()));
    }

    @Transactional
    public Map<String, Object> quit(UUID userId, UUID sessionId) {
        QuizSessionEntity entity = loadOwned(userId, sessionId);
        finish(entity, entity.getPayload(), userId);
        return toView(entity, null);
    }

    private void finishQuietly(QuizSessionEntity entity) {
        QuizSessionState state = entity.getPayload();
        if (state.getStage() == QuizStage.FINISHED) {
            return;
        }
        finish(entity, state, entity.getUserId());
    }

    private void finish(QuizSessionEntity entity, QuizSessionState state, UUID userId) {
        if (state.getStage() == QuizStage.FINISHED) {
            applyState(entity, state);
            sessions.save(entity);
            return;
        }
        state.setStage(QuizStage.FINISHED);
        entity.setFinished(true);
        entity.setFinishedAt(Instant.now());
        if (state.getAnsweredCount() > 0) {
            UserStatsEntity statsEntity = statsEntity(userId);
            StatsPayload payload = statsEntity.getPayload();
            StatsPayload.SessionRecord record = new StatsPayload.SessionRecord();
            record.startedAt = state.getStartedAt();
            record.finishedAt = entity.getFinishedAt();
            record.durationMillis = state.getElapsedMillis();
            record.answered = state.getAnsweredCount();
            record.correct = state.getCorrectCount();
            record.infinite = state.getConfig().isInfinite();
            record.targetCount = state.targetCount();
            record.topics = new ArrayList<>(state.getConfig().getTopicIds());
            payload.addSession(record);
            statsEntity.setPayload(payload);
            stats.save(statsEntity);
        }
        applyState(entity, state);
        sessions.save(entity);
    }

    /**
     * The setup step is the only place topics, count and level are chosen, so the round that
     * uses them writes them back and the step opens on them next time.
     *
     * <p>Skipped for a single-section round — that one is started from an article, to drill the
     * thing just read, and is not the learner saying "this is my quiz from now on".
     */
    private void rememberSetup(UUID userId, QuizConfig config, List<String> chosenTopics, boolean showExplanation) {
        if (config.getSectionId() != null) {
            return;
        }
        UserSettingsEntity entity = settings.findById(userId).orElse(null);
        if (entity == null) {
            return;
        }
        SettingsPayload payload = entity.getPayload() == null ? new SettingsPayload() : entity.getPayload();
        payload.setSelectedTopics(chosenTopics);
        payload.questionCount = config.getTargetCount();
        payload.infiniteMode = config.isInfinite();
        payload.level = config.getLevel();
        payload.shuffleOptions = config.isShuffleOptions();
        payload.smartSelection = config.isSmartSelection();
        payload.showExplanation = showExplanation;
        entity.setPayload(payload);
        settings.save(entity);
    }

    /** What the setup step opens on: the choice this learner made last time. */
    @Transactional
    public Map<String, Object> setup(UUID userId) {
        SettingsPayload payload = settings.findById(userId)
                .map(UserSettingsEntity::getPayload)
                .orElseGet(SettingsPayload::new);
        Map<String, Object> dto = new LinkedHashMap<>();
        // The saved list, not effectiveTopics: an empty selection means "all", and the step
        // shows that as nothing ticked rather than as everything ticked.
        dto.put("topicIds", new ArrayList<>(payload.selectedTopics));
        dto.put("questionCount", payload.normalizedQuestionCount());
        dto.put("infinite", payload.infiniteMode);
        dto.put("level", payload.level.name());
        dto.put("shuffleOptions", payload.shuffleOptions);
        dto.put("smartSelection", payload.smartSelection);
        dto.put("showExplanation", payload.showExplanation);
        return dto;
    }

    /** Stats row for the user, created on the fly so a missing row cannot fail a quiz action. */
    private UserStatsEntity statsEntity(UUID userId) {
        UserStatsEntity entity = stats.findById(userId).orElseGet(() -> {
            UserStatsEntity created = new UserStatsEntity();
            created.setUserId(userId);
            created.setPayload(new StatsPayload());
            return created;
        });
        if (entity.getPayload() == null) {
            entity.setPayload(new StatsPayload());
        }
        return entity;
    }

    private void nextQuestion(QuizSessionState state, List<Question> pool, QuestionPicker picker, StatsPayload statsPayload) {
        if (state.getDeck().isEmpty()) {
            refillDeck(state, pool, picker, statsPayload, state.getCurrentQuestionId());
        }
        if (state.getDeck().isEmpty()) {
            state.setStage(QuizStage.FINISHED);
            state.setCurrentQuestionId(null);
            return;
        }
        String nextId = state.getDeck().remove(0);
        Question next = pool.stream().filter(q -> q.getId().equals(nextId)).findFirst().orElse(null);
        if (next == null) {
            state.setStage(QuizStage.FINISHED);
            state.setCurrentQuestionId(null);
            return;
        }
        state.setCurrentQuestionId(next.getId());
        state.setAskedCount(state.getAskedCount() + 1);
        state.setStage(QuizStage.QUESTION_ONLY);
        state.setSelectedIndex(-1);
        state.setDisplayOptionIndexes(buildDisplayOrder(next, state.getConfig().isShuffleOptions()));
        state.setQuestionShownAt(Instant.now());
    }

    private void refillDeck(
            QuizSessionState state,
            List<Question> pool,
            QuestionPicker picker,
            StatsPayload statsPayload,
            String currentId) {
        if (pool.isEmpty()) {
            return;
        }
        int want = state.getConfig().isInfinite()
                ? pool.size()
                : Math.max(0, state.getConfig().getTargetCount() - state.getAskedCount());
        if (want <= 0) {
            return;
        }
        List<Question> next = picker.pick(pool, want, state.getConfig().isSmartSelection(), statsPayload);
        if (next.isEmpty()) {
            return;
        }
        if (currentId != null && next.size() > 1 && next.get(0).getId().equals(currentId)) {
            Collections.swap(next, 0, 1);
        }
        List<String> deck = new ArrayList<>(state.getDeck());
        for (Question question : next) {
            deck.add(question.getId());
        }
        state.setDeck(deck);
    }

    private List<Integer> buildDisplayOrder(Question question, boolean shuffle) {
        List<Integer> indexes = question.getOptions().stream()
                .map(QuestionOption::getOptionIndex)
                .collect(Collectors.toCollection(ArrayList::new));
        if (indexes.isEmpty()) {
            indexes = IntStream.range(0, 5).boxed().collect(Collectors.toCollection(ArrayList::new));
        }
        if (shuffle) {
            Collections.shuffle(indexes, random);
        }
        return indexes;
    }

    private QuizConfig resolveConfig(QuizStartRequest request, SettingsPayload settingsPayload,
                                     List<String> chosenTopics) {
        QuizConfig config = new QuizConfig();
        config.setShuffleOptions(request != null && request.shuffleOptions != null
                ? request.shuffleOptions
                : settingsPayload.shuffleOptions);
        config.setSmartSelection(request != null && request.smartSelection != null
                ? request.smartSelection
                : settingsPayload.smartSelection);
        config.setLevel(request != null && request.level != null ? request.level : settingsPayload.level);
        List<Topic> catalog = topics.findAllByOrderBySortOrderAsc();
        if (request != null && request.sectionId != null && !request.sectionId.isBlank()
                && request.topicIds != null && request.topicIds.size() == 1) {
            config.setTopicIds(request.topicIds);
            config.setSectionId(request.sectionId);
            config.setTargetCount(request.targetCount != null ? Math.max(1, request.targetCount) : 10);
            config.setInfinite(false);
            return config;
        }
        config.setTopicIds(SettingsPayload.effectiveTopics(catalog, chosenTopics));
        config.setTargetCount(request != null && request.targetCount != null
                ? Math.max(1, Math.min(500, request.targetCount))
                : settingsPayload.normalizedQuestionCount());
        config.setInfinite(request != null && request.infinite != null
                ? request.infinite
                : settingsPayload.infiniteMode);
        return config;
    }

    private List<Question> loadPool(QuizConfig config) {
        List<Level> levels = config.getLevel().andBelow();
        if (config.getSectionId() != null && config.getTopicIds().size() == 1) {
            return questions.findByTopicIdAndSectionIdAndLevelIn(
                    config.getTopicIds().get(0), config.getSectionId(), levels);
        }
        return questions.findByTopicIdInAndLevelIn(config.getTopicIds(), levels);
    }

    private Question currentQuestion(QuizSessionState state, List<Question> pool) {
        if (state.getCurrentQuestionId() == null) {
            return null;
        }
        return pool.stream()
                .filter(question -> question.getId().equals(state.getCurrentQuestionId()))
                .findFirst()
                .orElseGet(() -> loadQuestion(state.getCurrentQuestionId()));
    }

    private Question loadQuestion(String id) {
        if (id == null) {
            return null;
        }
        return questions.findById(id).orElse(null);
    }

    private QuizSessionEntity loadOwned(UUID userId, UUID sessionId) {
        QuizSessionEntity entity = sessions.findById(sessionId)
                .orElseThrow(() -> new ApiException(Status.NOT_FOUND, "Quiz session not found"));
        if (!entity.getUserId().equals(userId)) {
            throw new ApiException(Status.FORBIDDEN, "Quiz session not found");
        }
        return entity;
    }

    private void applyState(QuizSessionEntity entity, QuizSessionState state) {
        entity.setPayload(state);
        entity.setStage(state.getStage().name());
        entity.setFinished(state.getStage() == QuizStage.FINISHED);
        if (entity.isFinished() && entity.getFinishedAt() == null) {
            entity.setFinishedAt(Instant.now());
        }
    }

    private Map<String, Object> toView(QuizSessionEntity entity, Question question) {
        QuizSessionState state = entity.getPayload();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", entity.getId());
        view.put("stage", state.getStage().name());
        view.put("askedCount", state.getAskedCount());
        view.put("answeredCount", state.getAnsweredCount());
        view.put("correctCount", state.getCorrectCount());
        view.put("streak", state.getStreak());
        view.put("bestStreak", state.getBestStreak());
        view.put("elapsedMillis", state.getElapsedMillis());
        view.put("accuracy", state.accuracy());
        view.put("targetCount", state.targetCount());
        view.put("infinite", state.getConfig().isInfinite());
        view.put("showExplanation", state.isShowExplanation());
        view.put("weakSectionKeys", new ArrayList<>(state.getWeakSectionKeys()));
        view.put("empty", state.getPoolIds().isEmpty());
        if (state.getStage() == QuizStage.FINISHED && !state.getWeakSectionKeys().isEmpty()) {
            view.put("weakSections", weakSections(state.getWeakSectionKeys()));
        }

        if (question != null && state.getStage() != QuizStage.FINISHED) {
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("id", question.getId());
            q.put("topicId", question.getTopicId());
            q.put("sectionId", question.getSectionId());
            q.put("difficulty", question.getDifficulty().name().toLowerCase());
            q.put("text", LocalizedTextDto.of(question.getTextEn(), question.getTextRu()));
            q.put("code", question.getCode());
            if (state.getStage() != QuizStage.QUESTION_ONLY) {
                List<Map<String, Object>> options = new ArrayList<>();
                int correctIndex = -1;
                List<Integer> order = state.getDisplayOptionIndexes();
                for (int i = 0; i < order.size(); i++) {
                    int original = order.get(i);
                    QuestionOption option = question.getOptions().stream()
                            .filter(item -> item.getOptionIndex() == original)
                            .findFirst()
                            .orElse(null);
                    if (option == null) {
                        continue;
                    }
                    Map<String, Object> optionDto = new LinkedHashMap<>();
                    optionDto.put("text", LocalizedTextDto.of(option.getTextEn(), option.getTextRu()));
                    if (state.getStage() == QuizStage.ANSWERED) {
                        optionDto.put("correct", option.isCorrect());
                    }
                    if (option.isCorrect()) {
                        correctIndex = i;
                    }
                    options.add(optionDto);
                }
                q.put("options", options);
                if (state.getStage() == QuizStage.ANSWERED) {
                    q.put("selectedIndex", state.getSelectedIndex());
                    q.put("correctIndex", correctIndex);
                    if (state.isShowExplanation()) {
                        q.put("explanation", LocalizedTextDto.of(question.getExplanationEn(), question.getExplanationRu()));
                    }
                    q.put("sources", question.getSources().stream().map(QuestionSource::getUrl).toList());
                }
            }
            view.put("question", q);
        }
        return view;
    }

    /** Weak sections with their titles, so the summary can offer readable "re-read this" links. */
    private List<Map<String, Object>> weakSections(Collection<String> keys) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String key : keys) {
            int slash = key.indexOf('/');
            String topicId = slash < 0 ? key : key.substring(0, slash);
            String sectionId = slash < 0 ? "" : key.substring(slash + 1);
            Topic topic = topics.findById(topicId).orElse(null);
            TopicSection section = sections.findById(new TopicSection.Id(topicId, sectionId)).orElse(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", key);
            row.put("topicId", topicId);
            row.put("sectionId", sectionId);
            row.put("topicName", topic == null
                    ? LocalizedTextDto.of(topicId, topicId)
                    : LocalizedTextDto.of(topic.getNameEn(), topic.getNameRu()));
            row.put("sectionTitle", section == null
                    ? LocalizedTextDto.of(sectionId, sectionId)
                    : LocalizedTextDto.of(section.getTitleEn(), section.getTitleRu()));
            rows.add(row);
        }
        return rows;
    }

    /**
     * Body of {@code POST /api/quiz/start} — the setup step, all of it optional. Anything left
     * out falls back to what was saved the last time a round was started.
     */
    public static class QuizStartRequest {
        public List<String> topicIds;
        public String sectionId;
        public Integer targetCount;
        public Boolean infinite;
        /** Overrides the saved level for one session, the way topicIds and targetCount do. */
        public Level level;
        public Boolean shuffleOptions;
        public Boolean smartSelection;
        public Boolean showExplanation;
    }
}
