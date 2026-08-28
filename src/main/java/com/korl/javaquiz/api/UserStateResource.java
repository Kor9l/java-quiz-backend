package com.korl.javaquiz.api;

import com.korl.javaquiz.api.dto.SettingsRequest;
import com.korl.javaquiz.security.CurrentUser;
import com.korl.javaquiz.service.StatsService;
import com.korl.javaquiz.service.UserStateService;
import com.korl.javaquiz.userstate.SettingsPayload;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class UserStateResource {

    private final UserStateService userStateService;
    private final StatsService statsService;
    private final CurrentUser currentUser;

    public UserStateResource(UserStateService userStateService, StatsService statsService, CurrentUser currentUser) {
        this.userStateService = userStateService;
        this.statsService = statsService;
        this.currentUser = currentUser;
    }

    @GET
    @Path("/settings")
    public SettingsPayload getSettings() {
        return userStateService.getSettings(currentUser.id());
    }

    @PUT
    @Path("/settings")
    @Consumes(MediaType.APPLICATION_JSON)
    public SettingsPayload saveSettings(SettingsRequest payload) {
        return userStateService.saveSettings(currentUser.id(), payload);
    }

    @GET
    @Path("/stats")
    public Map<String, Object> stats() {
        return statsService.get(currentUser.id());
    }

    /** Void bodies answer 204, which is what the UI expects from every reset. */
    @POST
    @Path("/stats/reset")
    public void resetStats() {
        statsService.reset(currentUser.id());
    }

    @POST
    @Path("/progress/{topicId}/{sectionId}/read")
    public void markRead(@PathParam("topicId") String topicId, @PathParam("sectionId") String sectionId) {
        userStateService.markRead(currentUser.id(), topicId, sectionId);
    }

    @POST
    @Path("/progress/{topicId}/{sectionId}/unread")
    public void markUnread(@PathParam("topicId") String topicId, @PathParam("sectionId") String sectionId) {
        userStateService.markUnread(currentUser.id(), topicId, sectionId);
    }

    @POST
    @Path("/progress/reset")
    public void resetProgress() {
        userStateService.resetProgress(currentUser.id());
    }
}
