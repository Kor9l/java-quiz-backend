package com.korl.javaquiz.service;

import com.korl.javaquiz.api.dto.LocalizedTextDto;
import com.korl.javaquiz.api.error.ApiException;
import com.korl.javaquiz.domain.Difficulty;
import com.korl.javaquiz.domain.PracticeDataset;
import com.korl.javaquiz.domain.PracticeDatasetRepository;
import com.korl.javaquiz.domain.PracticeProgressEntity;
import com.korl.javaquiz.domain.PracticeProgressRepository;
import com.korl.javaquiz.domain.PracticeTask;
import com.korl.javaquiz.domain.PracticeTaskRepository;
import com.korl.javaquiz.practice.CompileDiagnostic;
import com.korl.javaquiz.practice.JavaPracticeEngine;
import com.korl.javaquiz.practice.JavaTaskSpec;
import com.korl.javaquiz.practice.PracticeSubmissionException;
import com.korl.javaquiz.practice.ResultComparator;
import com.korl.javaquiz.practice.ResultTable;
import com.korl.javaquiz.practice.SchemaInfo;
import com.korl.javaquiz.practice.SqlPracticeEngine;
import com.korl.javaquiz.practice.SubmissionOutcome;
import com.korl.javaquiz.practice.TaskSpec;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response.Status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The practice section: hands-on exercises the learner solves by writing code that is then
 * run, rather than by picking an option.
 *
 * <p>Two tracks share everything except grading. Navigation, progress, difficulty and the link
 * back to the study material are written once here; which engine a submission goes to is the
 * one thing that follows from the task's track.
 */
@ApplicationScoped
public class PracticeService {

    static final String JAVA_TRACK = "java";

    private final PracticeTaskRepository tasks;
    private final PracticeDatasetRepository datasets;
    private final PracticeProgressRepository progress;
    private final SqlPracticeEngine engine;
    private final JavaPracticeEngine javaEngine;

    public PracticeService(
            PracticeTaskRepository tasks,
            PracticeDatasetRepository datasets,
            PracticeProgressRepository progress,
            SqlPracticeEngine engine,
            JavaPracticeEngine javaEngine) {
        this.tasks = tasks;
        this.datasets = datasets;
        this.progress = progress;
        this.engine = engine;
        this.javaEngine = javaEngine;
    }

    /** The tracks available and how far the user has got in each. */
    @Transactional
    public List<Map<String, Object>> listTracks(UUID userId) {
        Map<String, PracticeProgressEntity> solved = progressByTask(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (String track : tasks.findTracks()) {
            result.add(trackSummary(track, tasks.findByTrackOrderByDifficultyAscSortOrderAsc(track), solved));
        }
        return result;
    }

    /** One track broken down by difficulty, which is the level the UI navigates at. */
    @Transactional
    public Map<String, Object> track(UUID userId, String track) {
        List<PracticeTask> trackTasks = tasks.findByTrackOrderByDifficultyAscSortOrderAsc(track);
        if (trackTasks.isEmpty()) {
            throw new ApiException(Status.NOT_FOUND, "Unknown practice track: " + track);
        }
        return trackSummary(track, trackTasks, progressByTask(userId));
    }

    @Transactional
    public List<Map<String, Object>> listTasks(UUID userId, String track, Difficulty difficulty) {
        Map<String, PracticeProgressEntity> byTask = progressByTask(userId);
        return tasks.findByTrackAndDifficultyOrderBySortOrderAsc(track, difficulty).stream()
                .map(task -> taskSummary(task, byTask.get(task.getId())))
                .toList();
    }

    /**
     * Everything needed to attempt one task: the statement, the shape of the dataset, the
     * result being aimed at, and whatever the learner last submitted.
     */
    @Transactional
    public Map<String, Object> task(UUID userId, String taskId) {
        PracticeTask task = requireTask(taskId);
        PracticeProgressEntity state = progress
                .findById(new PracticeProgressEntity.Id(userId, taskId))
                .orElse(null);

        Map<String, Object> dto = new LinkedHashMap<>(taskSummary(task, state));
        dto.put("statement", LocalizedTextDto.of(task.getStatementEn(), task.getStatementRu()));
        dto.put("hint", LocalizedTextDto.of(task.getHintEn(), task.getHintRu()));
        if (isJava(task)) {
            dto.put("className", task.getClassName());
            dto.put("starterCode", task.getStarterCode());
            dto.put("lastCode", state == null ? null : state.getLastSubmission());
            // The cases are the specification, so they are shown rather than held back.
            dto.put("cases", casesDto(task));
            dto.put("expected", tableDto(javaEngine.expectedResult(javaSpec(task))));
        } else {
            PracticeDataset dataset = requireDataset(task.getDatasetId());
            dto.put("starterSql", task.getStarterSql());
            dto.put("lastSql", state == null ? null : state.getLastSubmission());
            dto.put("dataset", datasetDto(dataset));
            dto.put("expected",
                    tableDto(engine.expectedResult(spec(task, dataset)).preview(engine.limits().previewRows())));
        }
        // Held back until they get there, the same way the quiz reveals an explanation only
        // once the question has been answered.
        if (state != null && state.isSolved()) {
            dto.put("explanation", LocalizedTextDto.of(task.getExplanationEn(), task.getExplanationRu()));
        }
        return dto;
    }

    /**
     * Checks a submission without running it and without touching the user's record. On the SQL
     * track that means parsing it, on the Java track compiling it — the same question asked of
     * two languages.
     */
    @Transactional
    public Map<String, Object> check(String taskId, String submission) {
        PracticeTask task = requireTask(taskId);
        if (isJava(task)) {
            JavaTaskSpec spec = javaSpec(task);
            return graded(task, () -> javaEngine.checkCompilation(spec, submission), false);
        }
        TaskSpec spec = spec(task, requireDataset(task.getDatasetId()));
        return graded(task, () -> engine.checkSyntax(spec, submission), false);
    }

    /** Runs a submission, grades it against the reference result, and records the attempt. */
    @Transactional
    public Map<String, Object> run(UUID userId, String taskId, String submission) {
        PracticeTask task = requireTask(taskId);
        Map<String, Object> response;
        if (isJava(task)) {
            JavaTaskSpec spec = javaSpec(task);
            response = graded(task, () -> javaEngine.grade(spec, submission), true);
        } else {
            TaskSpec spec = spec(task, requireDataset(task.getDatasetId()));
            response = graded(task, () -> engine.grade(spec, submission), true);
        }
        record(userId, taskId, submission, Boolean.TRUE.equals(response.get("passed")));
        return response;
    }

    /**
     * Turns a grading run into a response. A rejected submission is a normal outcome here,
     * not an API error — the learner asked to be told what is wrong with their SQL, and the
     * status field is the answer.
     */
    private Map<String, Object> graded(PracticeTask task, Grading grading, boolean revealOnPass) {
        SubmissionOutcome outcome;
        try {
            outcome = grading.run();
        } catch (PracticeSubmissionException e) {
            Map<String, Object> failure = new LinkedHashMap<>();
            failure.put("status", e.getStatus().name());
            failure.put("passed", false);
            failure.put("messageKey", e.getMessageKey());
            failure.put("detail", e.getDetail());
            return failure;
        }
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("status", outcome.status().name());
        dto.put("passed", outcome.passed());
        dto.put("messageKey", outcome.messageKey());
        dto.put("detail", outcome.detail());
        dto.put("durationMs", outcome.durationMs());
        dto.put("result", tableDto(outcome.result()));
        dto.put("expected", tableDto(outcome.expected()));
        dto.put("comparison", comparisonDto(outcome.comparison()));
        // Present on both tracks, empty on the SQL one, so that a client does not have to know
        // which track it is looking at to read the response.
        dto.put("diagnostics", outcome.diagnostics().stream().map(PracticeService::diagnosticDto).toList());
        dto.put("output", outcome.output());
        if (revealOnPass && outcome.passed()) {
            dto.put("explanation", LocalizedTextDto.of(task.getExplanationEn(), task.getExplanationRu()));
        }
        return dto;
    }

    private void record(UUID userId, String taskId, String submission, boolean passed) {
        PracticeProgressEntity state = progress
                .findById(new PracticeProgressEntity.Id(userId, taskId))
                .orElseGet(() -> new PracticeProgressEntity(userId, taskId));
        state.record(submission, passed, Instant.now());
        progress.save(state);
    }

    private Map<String, Object> trackSummary(
            String track, List<PracticeTask> trackTasks, Map<String, PracticeProgressEntity> byTask) {
        Map<Difficulty, List<PracticeTask>> byDifficulty = trackTasks.stream()
                .collect(Collectors.groupingBy(PracticeTask::getDifficulty, () -> new EnumMap<>(Difficulty.class),
                        Collectors.toList()));
        List<Map<String, Object>> difficulties = new ArrayList<>();
        for (Difficulty difficulty : Difficulty.values()) {
            List<PracticeTask> group = byDifficulty.getOrDefault(difficulty, List.of());
            if (group.isEmpty()) {
                continue;
            }
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("difficulty", difficulty.name());
            dto.put("taskCount", group.size());
            dto.put("solvedCount", countSolved(group, byTask));
            difficulties.add(dto);
        }
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("track", track);
        dto.put("taskCount", trackTasks.size());
        dto.put("solvedCount", countSolved(trackTasks, byTask));
        dto.put("difficulties", difficulties);
        return dto;
    }

    private Map<String, Object> taskSummary(PracticeTask task, PracticeProgressEntity state) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", task.getId());
        dto.put("track", task.getTrack());
        dto.put("difficulty", task.getDifficulty().name());
        dto.put("order", task.getSortOrder());
        dto.put("title", LocalizedTextDto.of(task.getTitleEn(), task.getTitleRu()));
        dto.put("datasetId", task.getDatasetId());
        // The study section this exercise drills, so a stuck learner can go and read about it.
        dto.put("material", task.getSectionId() == null ? null : Map.of(
                "topicId", task.getTopicId(), "sectionId", task.getSectionId()));
        dto.put("orderMatters", task.isOrderMatters());
        dto.put("solved", state != null && state.isSolved());
        dto.put("attempts", state == null ? 0 : state.getAttempts());
        dto.put("sources", task.getSources().stream()
                .map(source -> Map.of("title", source.getTitle(), "url", source.getUrl()))
                .toList());
        return dto;
    }

    private Map<String, Object> datasetDto(PracticeDataset dataset) {
        SchemaInfo schema = engine.describeSchema(dataset.getId(), dataset.getSetupStatements());
        List<Map<String, Object>> tableDtos = schema.tables().stream()
                .map(table -> Map.<String, Object>of(
                        "name", table.name(),
                        "columns", table.columns().stream()
                                .map(column -> Map.of(
                                        "name", column.name(),
                                        "type", column.type(),
                                        "nullable", column.nullable()))
                                .toList()))
                .toList();
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", dataset.getId());
        dto.put("title", LocalizedTextDto.of(dataset.getTitleEn(), dataset.getTitleRu()));
        dto.put("description", LocalizedTextDto.of(dataset.getDescriptionEn(), dataset.getDescriptionRu()));
        dto.put("tables", tableDtos);
        return dto;
    }

    private static Map<String, Object> tableDto(ResultTable table) {
        if (table == null) {
            return null;
        }
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("columns", table.columns());
        dto.put("rows", table.rows());
        dto.put("truncated", table.truncated());
        return dto;
    }

    private static Map<String, Object> comparisonDto(ResultComparator.Comparison comparison) {
        if (comparison == null) {
            return null;
        }
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("matched", comparison.matched());
        dto.put("reasonKey", comparison.reasonKey());
        dto.put("firstDifference", comparison.firstDifference());
        dto.put("missingRows", comparison.missingRows());
        dto.put("unexpectedRows", comparison.unexpectedRows());
        return dto;
    }

    private static int countSolved(List<PracticeTask> group, Map<String, PracticeProgressEntity> byTask) {
        return (int) group.stream()
                .map(task -> byTask.get(task.getId()))
                .filter(state -> state != null && state.isSolved())
                .count();
    }

    private Map<String, PracticeProgressEntity> progressByTask(UUID userId) {
        return progress.findByIdUserId(userId).stream()
                .collect(Collectors.toMap(PracticeProgressEntity::taskId, state -> state));
    }

    private static Map<String, Object> diagnosticDto(CompileDiagnostic diagnostic) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("severity", diagnostic.severity());
        dto.put("line", diagnostic.line());
        dto.put("column", diagnostic.column());
        dto.put("message", diagnostic.message());
        dto.put("inSubmission", diagnostic.inSubmission());
        return dto;
    }

    /** The calls a Java task is graded by, which are part of the statement rather than hidden. */
    private static List<Map<String, Object>> casesDto(PracticeTask task) {
        return task.getCases().stream()
                .map(current -> Map.<String, Object>of(
                        "label", current.getLabel(), "expression", current.getExpression()))
                .toList();
    }

    private static boolean isJava(PracticeTask task) {
        return JAVA_TRACK.equals(task.getTrack());
    }

    private TaskSpec spec(PracticeTask task, PracticeDataset dataset) {
        return new TaskSpec(
                task.getId(), dataset.getSetupStatements(), task.getSolutionSql(), task.isOrderMatters());
    }

    private JavaTaskSpec javaSpec(PracticeTask task) {
        return new JavaTaskSpec(
                task.getId(),
                task.getClassName(),
                task.getSolutionCode(),
                task.getCases().stream()
                        .map(current -> new JavaTaskSpec.Case(current.getLabel(), current.getExpression()))
                        .toList());
    }

    private PracticeTask requireTask(String taskId) {
        return tasks.findById(taskId)
                .orElseThrow(() -> new ApiException(Status.NOT_FOUND, "Unknown practice task: " + taskId));
    }

    private PracticeDataset requireDataset(String datasetId) {
        return datasets.findById(datasetId)
                .orElseThrow(() -> new ApiException(Status.NOT_FOUND, "Unknown dataset: " + datasetId));
    }

    @FunctionalInterface
    private interface Grading {
        SubmissionOutcome run();
    }
}
