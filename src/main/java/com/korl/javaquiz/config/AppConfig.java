package com.korl.javaquiz.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

/**
 * Everything under the {@code app.} prefix. An interface rather than a bean with setters:
 * SmallRye binds and validates it at build time, so a missing value fails the boot instead
 * of surfacing as a null halfway through a request.
 */
@ConfigMapping(prefix = "app")
public interface AppConfig {

    Jwt jwt();

    Google google();

    Practice practice();

    /** Base URL of the UI, where the OAuth2 flow hands the JWT back to the browser. */
    @WithDefault("http://localhost")
    String frontendUrl();

    /** Base URL this backend is reachable at from the browser; must match the Google redirect URI host. */
    @WithDefault("http://localhost:8080")
    String publicUrl();

    interface Jwt {
        String secret();

        @WithDefault("86400000")
        long expirationMs();
    }

    /**
     * Optional on purpose: the app boots without Google credentials, and an unset variable
     * arrives as the empty string, which SmallRye rejects for a plain String property.
     */
    interface Google {
        Optional<String> clientId();

        Optional<String> clientSecret();

        default String id() {
            return clientId().map(String::trim).orElse("");
        }

        default String secret() {
            return clientSecret().map(String::trim).orElse("");
        }

        default boolean isConfigured() {
            return !id().isBlank();
        }
    }

    /** Limits applied to SQL written by learners in the practice section. */
    interface Practice {
        @WithDefault("5")
        int queryTimeoutSeconds();

        @WithDefault("500")
        int maxRows();

        @WithDefault("4000")
        int maxSqlLength();

        @WithDefault("50")
        int previewRows();
    }
}
