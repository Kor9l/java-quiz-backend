package com.korl.javaquiz.api;

import com.korl.javaquiz.api.dto.UserDto;
import com.korl.javaquiz.api.error.ApiException;
import com.korl.javaquiz.domain.Role;
import com.korl.javaquiz.service.AdminService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
@ApplicationScoped
public class AdminResource {

    private final AdminService adminService;

    public AdminResource(AdminService adminService) {
        this.adminService = adminService;
    }

    @GET
    @Path("/users")
    public List<Map<String, Object>> users() {
        return adminService.listUsers();
    }

    @PATCH
    @Path("/users/{id}/role")
    @Consumes(MediaType.APPLICATION_JSON)
    public UserDto changeRole(@PathParam("id") UUID id, Map<String, String> body) {
        String requested = body == null ? null : body.get("role");
        Role role;
        try {
            role = Role.valueOf(String.valueOf(requested).trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(Status.BAD_REQUEST, "Unknown role: " + requested);
        }
        return adminService.changeRole(id, role);
    }
}
