package com.korl.javaquiz.api;

import com.korl.javaquiz.api.dto.AuthResponse;
import com.korl.javaquiz.api.dto.GoogleLoginRequest;
import com.korl.javaquiz.api.dto.LoginRequest;
import com.korl.javaquiz.api.dto.RegisterRequest;
import com.korl.javaquiz.api.dto.UserDto;
import com.korl.javaquiz.security.UserPrincipal;
import com.korl.javaquiz.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/providers")
    public Map<String, Object> providers() {
        return authService.providers();
    }

    @PostMapping("/google")
    public AuthResponse google(@RequestBody(required = false) GoogleLoginRequest request) {
        return authService.googleLogin(request == null ? new GoogleLoginRequest() : request);
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.me(principal.getId());
    }
}
