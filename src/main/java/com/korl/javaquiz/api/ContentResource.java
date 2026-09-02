package com.korl.javaquiz.api;

import com.korl.javaquiz.api.error.ApiException;
import com.korl.javaquiz.domain.LearningModule;
import com.korl.javaquiz.security.CurrentUser;
import com.korl.javaquiz.service.ContentService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;

import java.util.List;
import java.util.Map;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ContentResource {

    private final ContentService contentService;
    private final CurrentUser currentUser;

    public ContentResource(ContentService contentService, CurrentUser currentUser) {
        this.contentService = contentService;
        this.currentUser = currentUser;
    }

    /**
     * The topics of one module. Absent {@code module} means backend, which is what the path
     * meant before there was a second module and what every existing caller still means by it.
     */
    @GET
    @Path("/topics")
    public List<Map<String, Object>> topics(@QueryParam("module") String module) {
        return contentService.listTopics(currentUser.id(), moduleOrBackend(module));
    }

    @GET
    @Path("/materials/{topicId}/{sectionId}")
    public Map<String, Object> material(@PathParam("topicId") String topicId,
                                        @PathParam("sectionId") String sectionId) {
        return contentService.material(currentUser.id(), topicId, sectionId);
    }

    /**
     * An unknown name is a 400 rather than a silent fall back to backend: a client asking for a
     * module this build does not have wants to hear about it, not to be handed the other one's
     * topics and left to wonder why nothing matches.
     */
    private static LearningModule moduleOrBackend(String value) {
        if (value == null || value.isBlank()) {
            return LearningModule.BACKEND;
        }
        return LearningModule.parse(value)
                .orElseThrow(() -> new ApiException(Status.BAD_REQUEST, "Unknown module: " + value));
    }
}
