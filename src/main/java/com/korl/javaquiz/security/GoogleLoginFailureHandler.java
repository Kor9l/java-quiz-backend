package com.korl.javaquiz.security;

import com.korl.javaquiz.config.AppProperties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Sends the user back to the login page with a readable reason instead of a Whitelabel page. */
@Component
public class GoogleLoginFailureHandler implements AuthenticationFailureHandler {

    private final AppProperties properties;

    public GoogleLoginFailureHandler(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String url = properties.getFrontendUrl();
        String base = url == null || url.isBlank() ? "http://localhost" : url.replaceAll("/+$", "");
        String message = exception.getMessage() == null ? "Google sign-in failed" : exception.getMessage();
        response.sendRedirect(base + "/login?googleError="
                + URLEncoder.encode(message, StandardCharsets.UTF_8));
    }
}
