package com.korl.javaquiz.api;

import com.korl.javaquiz.security.CurrentUser;
import com.korl.javaquiz.service.ContentService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

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
        return contentService.listTopics(currentUser.id(), ModuleParam.orBackend(module));
    }

    @GET
    @Path("/materials/{topicId}/{sectionId}")
    public Map<String, Object> material(@PathParam("topicId") String topicId,
                                        @PathParam("sectionId") String sectionId) {
        return contentService.material(currentUser.id(), topicId, sectionId);
    }
}
