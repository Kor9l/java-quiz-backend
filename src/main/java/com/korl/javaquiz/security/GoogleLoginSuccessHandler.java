package com.korl.javaquiz.security;

import com.korl.javaquiz.config.AppProperties;
import com.korl.javaquiz.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Turns a completed Google login into an app JWT and hands it to the UI through the URL
 * fragment, which browsers keep out of Referer headers and server logs.
 */
@Component
public class GoogleLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final AppProperties properties;

    public GoogleLoginSuccessHandler(AuthService authService, AppProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2User principal = (OAuth2User) authentication.getPrincipal();
        String googleId = principal.getAttribute("sub");
        String email = principal.getAttribute("email");
        String name = principal.getAttribute("name");

        String token = authService.loginWithGoogle(googleId, email, name);

        String target = UriComponentsBuilder.fromUriString(frontendBase())
                .path("/auth/callback")
                .build(true)
                .toUriString()
                + "#token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        response.sendRedirect(target);
    }

    private String frontendBase() {
        String url = properties.getFrontendUrl();
        return url == null || url.isBlank() ? "http://localhost" : url.replaceAll("/+$", "");
    }
}
