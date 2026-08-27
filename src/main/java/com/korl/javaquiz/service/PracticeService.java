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
import com.korl.javaquiz.practice.ResultComparator;
import com.korl.javaquiz.practice.ResultTable;
import com.korl.javaquiz.practice.SchemaInfo;
import com.korl.javaquiz.practice.SqlPracticeEngine;
import com.korl.javaquiz.practice.SqlSubmissionException;
import com.korl.javaquiz.practice.SubmissionOutcome;
import com.korl.javaquiz.practice.TaskSpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 */
@Service
public class PracticeService {

    private final PracticeTaskRepository tasks;
    private final PracticeDatasetRepository datasets;
    private final PracticeProgressRepository progress;
    private final SqlPracticeEngine engine;

    public PracticeService(
            PracticeTaskRepository tasks,
            PracticeDatasetRepository datasets,
            PracticeProgressRepository progress,
            SqlPracticeEngine engine) {
        this.tasks = tasks;
        this.datasets = datasets;
        this.progress = progress;
        this.engine = engine;
    }

    /** The tracks available and how far the user has got in each. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTracks(UUID userId) {
        Map<String, PracticeProgressEntity> solved = progressByTask(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (String track : tasks.findTracks()) {
            result.add(trackSummary(track, tasks.findByTrackOrderByDifficultyAscSortOrderAsc(track), solved));
        }
        return result;
    }

    /** One track broken down by difficulty, which is the level the UI navigates at. */
    @Transactional(readOnly = true)
    public Map<String, Object> track(UUID userId, String track) {
        List<PracticeTask> trackTasks = tasks.findByTrackOrderByDifficultyAscSortOrderAsc(track);
        if (trackTasks.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Unknown practice track: " + track);
        }
        return trackSummary(track, trackTasks, progressByTask(userId));
    }

    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public Map<String, Object> task(UUID userId, String taskId) {
        PracticeTask task = requireTask(taskId);
        PracticeDataset dataset = requireDataset(task.getDatasetId());
        PracticeProgressEntity state = progress
                .findById(new PracticeProgressEntity.Id(userId, taskId))
                .orElse(null);

        Map<String, Object> dto = new LinkedHashMap<>(taskSummary(task, state));
        dto.put("statement", LocalizedTextDto.of(task.getStatementEn(), task.getStatementRu()));
        dto.put("hint", LocalizedTextDto.of(task.getHintEn(), task.getHintRu()));
        dto.put("starterSql", task.getStarterSql());
        dto.put("lastSql", state == null ? null : state.getLastSql());
        dto.put("dataset", datasetDto(dataset));
        dto.put("expected", tableDto(engine.expectedResult(spec(task, dataset)).preview(engine.limits().previewRows())));
        // Held back until they get there, the same way the quiz reveals an explanation only
        // once the question has been answered.
        if (state != null && state.isSolved()) {
            dto.put("explanation", LocalizedTextDto.of(task.getExplanationEn(), task.getExplanationRu()));
        }
        return dto;
    }

    /** Parses a submission without running it and without touching the user's record. */
    @Transactional(readOnly = true)
    public Map<String, Object> check(String taskId, String sql) {
        PracticeTask task = requireTask(taskId);
        TaskSpec spec = spec(task, requireDataset(task.getDatasetId()));
        return graded(task, () -> engine.checkSyntax(spec, sql), false);
    }

    /** Runs a submission, grades it against the reference result, and records the attempt. */
    @Transactional
    public Map<String, Object> run(UUID userId, String taskId, String sql) {
        PracticeTask task = requireTask(taskId);
        TaskSpec spec = spec(task, requireDataset(task.getDatasetId()));
        Map<String, Object> response = graded(task, () -> engine.grade(spec, sql), true);
        record(userId, taskId, sql, Boolean.TRUE.equals(response.get("passed")));
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
        } catch (SqlSubmissionException e) {
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
        if (revealOnPass && outcome.passed()) {
            dto.put("explanation", LocalizedTextDto.of(task.getExplanationEn(), task.getExplanationRu()));
        }
        return dto;
    }

    private void record(UUID userId, String taskId, String sql, boolean passed) {
        PracticeProgressEntity state = progress
                .findById(new PracticeProgressEntity.Id(userId, taskId))
                .orElseGet(() -> new PracticeProgressEntity(userId, taskId));
        state.record(sql, passed, Instant.now());
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

    private TaskSpec spec(PracticeTask task, PracticeDataset dataset) {
        return new TaskSpec(
                task.getId(), dataset.getSetupStatements(), task.getSolutionSql(), task.isOrderMatters());
    }

    private PracticeTask requireTask(String taskId) {
        return tasks.findById(taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Unknown practice task: " + taskId));
    }

    private PracticeDataset requireDataset(String datasetId) {
        return datasets.findById(datasetId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Unknown dataset: " + datasetId));
    }

    @FunctionalInterface
    private interface Grading {
        SubmissionOutcome run();
    }
}
