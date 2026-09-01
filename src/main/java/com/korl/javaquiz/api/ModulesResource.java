package com.korl.javaquiz.api;

import com.korl.javaquiz.security.CurrentUser;
import com.korl.javaquiz.service.ModuleService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

/** What the learner picks between once they are signed in. */
@Path("/api/modules")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ModulesResource {

    private final ModuleService moduleService;
    private final CurrentUser currentUser;

    public ModulesResource(ModuleService moduleService, CurrentUser currentUser) {
        this.moduleService = moduleService;
        this.currentUser = currentUser;
    }

    @GET
    public List<Map<String, Object>> modules() {
        return moduleService.list(currentUser.id());
    }
}
