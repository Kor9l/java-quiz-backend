package com.korl.javaquiz.api;

import com.korl.javaquiz.api.dto.AuthResponse;
import com.korl.javaquiz.api.dto.LoginRequest;
import com.korl.javaquiz.api.dto.RegisterRequest;
import com.korl.javaquiz.api.dto.UserDto;
import com.korl.javaquiz.security.CurrentUser;
import com.korl.javaquiz.service.AuthService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AuthResource {

    private final AuthService authService;
    private final CurrentUser currentUser;

    public AuthResource(AuthService authService, CurrentUser currentUser) {
        this.authService = authService;
        this.currentUser = currentUser;
    }

    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    public AuthResponse register(@Valid RegisterRequest request) {
        return authService.register(request);
    }

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    public AuthResponse login(@Valid LoginRequest request) {
        return authService.login(request);
    }

    @GET
    @Path("/providers")
    public Map<String, Object> providers() {
        return authService.providers();
    }

    @GET
    @Path("/me")
    public UserDto me() {
        return authService.me(currentUser.id());
    }
}
