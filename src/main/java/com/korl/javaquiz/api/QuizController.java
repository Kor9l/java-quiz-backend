package com.korl.javaquiz.api;

import com.korl.javaquiz.security.UserPrincipal;
import com.korl.javaquiz.service.QuizService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/quiz")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @PostMapping("/start")
    public Map<String, Object> start(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) QuizService.QuizStartRequest request) {
        return quizService.start(principal.getId(), request);
    }

    @GetMapping("/current")
    public Map<String, Object> current(@AuthenticationPrincipal UserPrincipal principal) {
        return quizService.current(principal.getId());
    }

    @PostMapping("/{id}/reveal")
    public Map<String, Object> reveal(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return quizService.reveal(principal.getId(), id);
    }

    @PostMapping("/{id}/answer")
    public Map<String, Object> answer(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody Map<String, Integer> body) {
        Integer optionIndex = body == null ? null : body.get("optionIndex");
        return quizService.answer(principal.getId(), id, optionIndex == null ? -1 : optionIndex);
    }

    @PostMapping("/{id}/advance")
    public Map<String, Object> advance(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return quizService.advance(principal.getId(), id);
    }

    @PostMapping("/{id}/quit")
    public Map<String, Object> quit(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return quizService.quit(principal.getId(), id);
    }
}
