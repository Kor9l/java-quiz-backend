package com.korl.javaquiz.api;

import com.korl.javaquiz.api.dto.SqlSubmissionRequest;
import com.korl.javaquiz.api.error.ApiException;
import com.korl.javaquiz.domain.Difficulty;
import com.korl.javaquiz.security.CurrentUser;
import com.korl.javaquiz.service.PracticeService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Path("/api/practice")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class PracticeResource {

    private final PracticeService practiceService;
    private final CurrentUser currentUser;

    public PracticeResource(PracticeService practiceService, CurrentUser currentUser) {
        this.practiceService = practiceService;
        this.currentUser = currentUser;
    }

    @GET
    public List<Map<String, Object>> tracks() {
        return practiceService.listTracks(currentUser.id());
    }

    @GET
    @Path("/tracks/{track}")
    public Map<String, Object> track(@PathParam("track") String track) {
        return practiceService.track(currentUser.id(), track);
    }

    @GET
    @Path("/tracks/{track}/{difficulty}")
    public List<Map<String, Object>> tasks(@PathParam("track") String track,
                                           @PathParam("difficulty") String difficulty) {
        return practiceService.listTasks(currentUser.id(), track, parseDifficulty(difficulty));
    }

    @GET
    @Path("/tasks/{taskId}")
    public Map<String, Object> task(@PathParam("taskId") String taskId) {
        return practiceService.task(currentUser.id(), taskId);
    }

    /** Parses a submission and reports what is wrong with it, without running it. */
    @POST
    @Path("/tasks/{taskId}/check")
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, Object> check(@PathParam("taskId") String taskId,
                                     @Valid SqlSubmissionRequest request) {
        return practiceService.check(taskId, request.sql);
    }

    /** Runs a submission against the sandbox and grades it. */
    @POST
    @Path("/tasks/{taskId}/run")
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, Object> run(@PathParam("taskId") String taskId,
                                   @Valid SqlSubmissionRequest request) {
        return practiceService.run(currentUser.id(), taskId, request.sql);
    }

    private static Difficulty parseDifficulty(String value) {
        try {
            return Difficulty.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(Status.NOT_FOUND, "Unknown difficulty: " + value);
        }
    }
}
