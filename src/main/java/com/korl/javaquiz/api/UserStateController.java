package com.korl.javaquiz.api;

import com.korl.javaquiz.security.UserPrincipal;
import com.korl.javaquiz.service.StatsService;
import com.korl.javaquiz.service.UserStateService;
import com.korl.javaquiz.userstate.SettingsPayload;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class UserStateController {

    private final UserStateService userStateService;
    private final StatsService statsService;

    public UserStateController(UserStateService userStateService, StatsService statsService) {
        this.userStateService = userStateService;
        this.statsService = statsService;
    }

    @GetMapping("/settings")
    public SettingsPayload getSettings(@AuthenticationPrincipal UserPrincipal principal) {
        return userStateService.getSettings(principal.getId());
    }

    @PutMapping("/settings")
    public SettingsPayload saveSettings(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody SettingsPayload payload) {
        return userStateService.saveSettings(principal.getId(), payload);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats(@AuthenticationPrincipal UserPrincipal principal) {
        return statsService.get(principal.getId());
    }

    @PostMapping("/stats/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetStats(@AuthenticationPrincipal UserPrincipal principal) {
        statsService.reset(principal.getId());
    }

    @PostMapping("/progress/{topicId}/{sectionId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String topicId,
            @PathVariable String sectionId) {
        userStateService.markRead(principal.getId(), topicId, sectionId);
    }

    @PostMapping("/progress/{topicId}/{sectionId}/unread")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markUnread(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String topicId,
            @PathVariable String sectionId) {
        userStateService.markUnread(principal.getId(), topicId, sectionId);
    }

    @PostMapping("/progress/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetProgress(@AuthenticationPrincipal UserPrincipal principal) {
        userStateService.resetProgress(principal.getId());
    }
}
