package com.korl.javaquiz.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.korl.javaquiz.config.AppConfig;
import com.korl.javaquiz.service.AuthService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * The Google authorization code flow, at the URLs Spring Security's {@code oauth2Login} used —
 * the redirect URI registered in the Google console is one of them, so they had to stay.
 *
 * <p>Written out rather than delegated to an OIDC extension because that is the whole of what is
 * needed: two endpoints, one form POST, and no session. Spring kept the authorization request in
 * an HTTP session; the CSRF {@code state} lives in a short-lived HttpOnly cookie here instead, so
 * the service stays stateless.
 */
@Path("")
@ApplicationScoped
public class GoogleOAuthResource {

    public static final String PROVIDER = "google";

    private static final String AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String SCOPE = "openid profile email";
    private static final String STATE_COOKIE = "javaquiz_oauth2_state";
    private static final int STATE_TTL_SECONDS = 300;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppConfig config;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public GoogleOAuthResource(AppConfig config, AuthService authService, ObjectMapper objectMapper) {
        this.config = config;
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    /** Holder so the HTTP client is only built the first time somebody actually signs in with Google. */
    private static final class Http {
        private static final HttpClient CLIENT = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @GET
    @Path("/oauth2/authorization/google")
    public Response authorize() {
        if (!config.google().isConfigured()) {
            return loginError("Google Sign-In is not configured");
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        String target = AUTHORIZATION_ENDPOINT
                + "?response_type=code"
                + "&client_id=" + encode(config.google().id())
                + "&scope=" + encode(SCOPE)
                + "&state=" + encode(state)
                + "&redirect_uri=" + encode(redirectUri());

        return Response.status(Response.Status.FOUND)
                .location(URI.create(target))
                .cookie(stateCookie(state, STATE_TTL_SECONDS))
                .build();
    }

    @GET
    @Path("/login/oauth2/code/google")
    public Response callback(
            @QueryParam("code") String code,
            @QueryParam("state") String state,
            @QueryParam("error") String error,
            @CookieParam(STATE_COOKIE) String expectedState) {

        // Whatever happens, the one-shot state cookie is spent.
        NewCookie cleared = stateCookie("", 0);
        try {
            if (error != null && !error.isBlank()) {
                return loginError(error, cleared);
            }
            if (!config.google().isConfigured()) {
                return loginError("Google Sign-In is not configured", cleared);
            }
            if (code == null || code.isBlank()) {
                return loginError("Google sign-in failed", cleared);
            }
            if (expectedState == null || state == null || !constantTimeEquals(expectedState, state)) {
                return loginError("Google sign-in failed: invalid state", cleared);
            }

            JsonNode claims = idTokenClaims(exchangeCode(code));
            if (!config.google().id().equals(claims.path("aud").asText())) {
                return loginError("Google sign-in failed: token issued for another client", cleared);
            }

            String token = authService.loginWithGoogle(
                    text(claims, "sub"), text(claims, "email"), text(claims, "name"));

            String target = frontendBase() + "/auth/callback#token=" + encode(token);
            return Response.status(Response.Status.FOUND)
                    .location(URI.create(target))
                    .cookie(cleared)
                    .build();
        } catch (Exception e) {
            String message = e.getMessage() == null ? "Google sign-in failed" : e.getMessage();
            return loginError(message, cleared);
        }
    }

    /** Swaps the authorization code for tokens. Google returns the id_token in this response. */
    private String exchangeCode(String code) throws Exception {
        String form = "grant_type=authorization_code"
                + "&code=" + encode(code)
                + "&client_id=" + encode(config.google().id())
                + "&client_secret=" + encode(config.google().secret())
                + "&redirect_uri=" + encode(redirectUri());

        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_ENDPOINT))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = Http.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Google rejected the authorization code");
        }
        String idToken = objectMapper.readTree(response.body()).path("id_token").asText(null);
        if (idToken == null || idToken.isBlank()) {
            throw new IllegalStateException("Google returned no id_token");
        }
        return idToken;
    }

    /**
     * Reads the id_token payload without verifying its signature. That is sound here and only
     * here: the token came back over TLS straight from Google's token endpoint, in the response
     * to a request carrying the client secret, so it cannot have been substituted in transit.
     */
    private JsonNode idTokenClaims(String idToken) throws Exception {
        String[] parts = idToken.split("\\.");
        if (parts.length < 2) {
            throw new IllegalStateException("Google returned a malformed id_token");
        }
        byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
        return objectMapper.readTree(payload);
    }

    private String redirectUri() {
        return publicBase() + "/login/oauth2/code/" + PROVIDER;
    }

    private NewCookie stateCookie(String value, int maxAge) {
        return new NewCookie.Builder(STATE_COOKIE)
                .value(value)
                .path("/")
                .httpOnly(true)
                .secure(publicBase().startsWith("https://"))
                .sameSite(NewCookie.SameSite.LAX)
                .maxAge(maxAge)
                .build();
    }

    private Response loginError(String message) {
        return loginError(message, null);
    }

    /** Sends the user back to the login page with a readable reason instead of a blank error page. */
    private Response loginError(String message, NewCookie cookie) {
        URI target = URI.create(frontendBase() + "/login?googleError=" + encode(message));
        Response.ResponseBuilder builder = Response.status(Response.Status.FOUND).location(target);
        if (cookie != null) {
            builder.cookie(cookie);
        }
        return builder.build();
    }

    private String frontendBase() {
        String url = config.frontendUrl();
        return url == null || url.isBlank() ? "http://localhost" : url.replaceAll("/+$", "");
    }

    private String publicBase() {
        String url = config.publicUrl();
        return url == null || url.isBlank() ? "http://localhost:8080" : url.replaceAll("/+$", "");
    }

    private static String text(JsonNode claims, String field) {
        return claims.path(field).asText(null);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
