package com.korl.javaquiz.service;

import com.korl.javaquiz.api.dto.AuthResponse;
import com.korl.javaquiz.api.dto.GoogleLoginRequest;
import com.korl.javaquiz.api.dto.LoginRequest;
import com.korl.javaquiz.api.dto.RegisterRequest;
import com.korl.javaquiz.api.dto.UserDto;
import com.korl.javaquiz.api.error.ApiException;
import com.korl.javaquiz.config.AppProperties;
import com.korl.javaquiz.domain.AppUser;
import com.korl.javaquiz.domain.AppUserRepository;
import com.korl.javaquiz.domain.AuthProvider;
import com.korl.javaquiz.domain.Role;
import com.korl.javaquiz.domain.UserProgressEntity;
import com.korl.javaquiz.domain.UserProgressRepository;
import com.korl.javaquiz.domain.UserSettingsEntity;
import com.korl.javaquiz.domain.UserSettingsRepository;
import com.korl.javaquiz.domain.UserStatsEntity;
import com.korl.javaquiz.domain.UserStatsRepository;
import com.korl.javaquiz.security.JwtService;
import com.korl.javaquiz.userstate.ProgressPayload;
import com.korl.javaquiz.userstate.SettingsPayload;
import com.korl.javaquiz.userstate.StatsPayload;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private final AppUserRepository users;
    private final UserSettingsRepository settings;
    private final UserStatsRepository stats;
    private final UserProgressRepository progress;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties properties;

    public AuthService(
            AppUserRepository users,
            UserSettingsRepository settings,
            UserStatsRepository stats,
            UserProgressRepository progress,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AppProperties properties) {
        this.users = users;
        this.settings = settings;
        this.stats = stats;
        this.progress = progress;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email.trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password));
        user.setDisplayName(displayName(request.displayName, email));
        user.setRole(Role.USER);
        user.setAuthProvider(AuthProvider.EMAIL);
        user.setCreatedAt(Instant.now());
        users.save(user);
        createUserState(user.getId());
        return tokenResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        AppUser user = users.findByEmailIgnoreCase(request.email.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        if (user.getAuthProvider() != AuthProvider.EMAIL || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return tokenResponse(user);
    }

    public Map<String, Object> providers() {
        return Map.of(
                "email", true,
                "google", Map.of(
                        "enabled", false,
                        "configured", properties.getGoogle().isConfigured(),
                        "message", "Google Sign-In is a stub. Set GOOGLE_CLIENT_ID and implement token verification to enable it."
                )
        );
    }

    public AuthResponse googleLogin(GoogleLoginRequest request) {
        throw new ApiException(
                HttpStatus.NOT_IMPLEMENTED,
                "Google Sign-In is not implemented yet. Configure GOOGLE_CLIENT_ID and wire token verification.");
    }

    @Transactional(readOnly = true)
    public UserDto me(UUID userId) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
        return UserDto.from(user);
    }

    private void createUserState(UUID userId) {
        UserSettingsEntity settingsEntity = new UserSettingsEntity();
        settingsEntity.setUserId(userId);
        settingsEntity.setPayload(new SettingsPayload());
        settings.save(settingsEntity);

        UserStatsEntity statsEntity = new UserStatsEntity();
        statsEntity.setUserId(userId);
        statsEntity.setPayload(new StatsPayload());
        stats.save(statsEntity);

        UserProgressEntity progressEntity = new UserProgressEntity();
        progressEntity.setUserId(userId);
        progressEntity.setPayload(new ProgressPayload());
        progress.save(progressEntity);
    }

    private AuthResponse tokenResponse(AppUser user) {
        AuthResponse response = new AuthResponse();
        response.token = jwtService.createToken(user.getId(), user.getEmail(), user.getRole());
        response.user = UserDto.from(user);
        return response;
    }

    private static String displayName(String requested, String email) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
