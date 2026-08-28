package com.korl.javaquiz.api;

import com.korl.javaquiz.api.dto.SqlSubmissionRequest;
import com.korl.javaquiz.api.error.ApiException;
import com.korl.javaquiz.domain.Difficulty;
import com.korl.javaquiz.security.UserPrincipal;
import com.korl.javaquiz.service.PracticeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/practice")
public class PracticeController {

    private final PracticeService practiceService;

    public PracticeController(PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    @GetMapping
    public List<Map<String, Object>> tracks(@AuthenticationPrincipal UserPrincipal principal) {
        return practiceService.listTracks(principal.getId());
    }

    @GetMapping("/tracks/{track}")
    public Map<String, Object> track(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String track) {
        return practiceService.track(principal.getId(), track);
    }

    @GetMapping("/tracks/{track}/{difficulty}")
    public List<Map<String, Object>> tasks(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String track,
            @PathVariable String difficulty) {
        return practiceService.listTasks(principal.getId(), track, parseDifficulty(difficulty));
    }

    @GetMapping("/tasks/{taskId}")
    public Map<String, Object> task(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String taskId) {
        return practiceService.task(principal.getId(), taskId);
    }

    /** Parses a submission and reports what is wrong with it, without running it. */
    @PostMapping("/tasks/{taskId}/check")
    public Map<String, Object> check(
            @PathVariable String taskId,
            @Valid @RequestBody SqlSubmissionRequest request) {
        return practiceService.check(taskId, request.sql);
    }

    /** Runs a submission against the sandbox and grades it. */
    @PostMapping("/tasks/{taskId}/run")
    public Map<String, Object> run(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String taskId,
            @Valid @RequestBody SqlSubmissionRequest request) {
        return practiceService.run(principal.getId(), taskId, request.sql);
    }

    private static Difficulty parseDifficulty(String value) {
        try {
            return Difficulty.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Unknown difficulty: " + value);
        }
    }
}
