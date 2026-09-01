package com.korl.javaquiz.api;

import com.korl.javaquiz.english.WordQuizConfig;
import com.korl.javaquiz.security.CurrentUser;
import com.korl.javaquiz.service.EnglishSettingsService;
import com.korl.javaquiz.service.WordQuizService;
import com.korl.javaquiz.service.WordStatsService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** The English drilling loop and its statistics. */
@Path("/api/english")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class EnglishQuizResource {

    private final WordQuizService quizService;
    private final WordStatsService statsService;
    private final EnglishSettingsService settingsService;
    private final CurrentUser currentUser;

    public EnglishQuizResource(
            WordQuizService quizService,
            WordStatsService statsService,
            EnglishSettingsService settingsService,
            CurrentUser currentUser) {
        this.quizService = quizService;
        this.statsService = statsService;
        this.settingsService = settingsService;
        this.currentUser = currentUser;
    }

    /** What the setup step opens on — the choice this learner made last time. */
    @GET
    @Path("/quiz/setup")
    public Map<String, Object> setup() {
        WordQuizConfig config = settingsService.savedSetup(currentUser.id());
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("groupIds", config.getGroupIds());
        dto.put("targetCount", config.getTargetCount());
        dto.put("infinite", config.isInfinite());
        dto.put("direction", config.getDirection().name());
        dto.put("favoritesOnly", config.isFavoritesOnly());
        return dto;
    }

    @POST
    @Path("/quiz/start")
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, Object> start(WordQuizService.WordQuizStartRequest request) {
        return quizService.start(currentUser.id(), request);
    }

    @GET
    @Path("/quiz/current")
    public Map<String, Object> current() {
        return quizService.current(currentUser.id());
    }

    @POST
    @Path("/quiz/{id}/reveal")
    public Map<String, Object> reveal(@PathParam("id") UUID id) {
        return quizService.reveal(currentUser.id(), id);
    }

    @POST
    @Path("/quiz/{id}/answer")
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, Object> answer(@PathParam("id") UUID id, Map<String, Integer> body) {
        Integer optionIndex = body == null ? null : body.get("optionIndex");
        return quizService.answer(currentUser.id(), id, optionIndex == null ? -1 : optionIndex);
    }

    @POST
    @Path("/quiz/{id}/advance")
    public Map<String, Object> advance(@PathParam("id") UUID id) {
        return quizService.advance(currentUser.id(), id);
    }

    @POST
    @Path("/quiz/{id}/quit")
    public Map<String, Object> quit(@PathParam("id") UUID id) {
        return quizService.quit(currentUser.id(), id);
    }

    @GET
    @Path("/stats")
    public Map<String, Object> stats() {
        return statsService.get(currentUser.id());
    }

    @POST
    @Path("/stats/reset")
    public void resetStats() {
        statsService.reset(currentUser.id());
    }
}
