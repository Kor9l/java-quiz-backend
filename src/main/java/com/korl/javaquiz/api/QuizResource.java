package com.korl.javaquiz.api;

import com.korl.javaquiz.security.CurrentUser;
import com.korl.javaquiz.service.QuizService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;
import java.util.UUID;

@Path("/api/quiz")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class QuizResource {

    private final QuizService quizService;
    private final CurrentUser currentUser;

    public QuizResource(QuizService quizService, CurrentUser currentUser) {
        this.quizService = quizService;
        this.currentUser = currentUser;
    }

    /** What the setup step opens on — the choice this learner made last time. */
    @GET
    @Path("/setup")
    public Map<String, Object> setup() {
        return quizService.setup(currentUser.id());
    }

    @POST
    @Path("/start")
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, Object> start(QuizService.QuizStartRequest request) {
        return quizService.start(currentUser.id(), request);
    }

    @GET
    @Path("/current")
    public Map<String, Object> current() {
        return quizService.current(currentUser.id());
    }

    @POST
    @Path("/{id}/reveal")
    public Map<String, Object> reveal(@PathParam("id") UUID id) {
        return quizService.reveal(currentUser.id(), id);
    }

    @POST
    @Path("/{id}/answer")
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, Object> answer(@PathParam("id") UUID id, Map<String, Integer> body) {
        Integer optionIndex = body == null ? null : body.get("optionIndex");
        return quizService.answer(currentUser.id(), id, optionIndex == null ? -1 : optionIndex);
    }

    @POST
    @Path("/{id}/advance")
    public Map<String, Object> advance(@PathParam("id") UUID id) {
        return quizService.advance(currentUser.id(), id);
    }

    @POST
    @Path("/{id}/quit")
    public Map<String, Object> quit(@PathParam("id") UUID id) {
        return quizService.quit(currentUser.id(), id);
    }
}
