package com.korl.javaquiz.config;

import com.korl.javaquiz.security.GoogleLoginFailureHandler;
import com.korl.javaquiz.security.GoogleLoginSuccessHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Registers the Google OAuth2 client only when credentials are present, so the app still
 * boots with an empty GOOGLE_CLIENT_ID (Spring's own oauth2 client properties would fail).
 */
@Configuration
@ConditionalOnExpression("'${app.google.client-id:}'.trim().length() > 0")
public class GoogleOAuthConfig {

    public static final String REGISTRATION_ID = "google";

    /**
     * The OAuth2 redirect dance needs a session to hold the authorization request state, so it
     * gets its own chain — the API chain below stays stateless and JWT-only.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain oauth2LoginFilterChain(
            HttpSecurity http,
            ClientRegistrationRepository clientRegistrationRepository,
            GoogleLoginSuccessHandler successHandler,
            GoogleLoginFailureHandler failureHandler) throws Exception {
        http
                .securityMatcher("/oauth2/authorization/**", "/login/oauth2/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .oauth2Login(oauth2 -> oauth2
                        .clientRegistrationRepository(clientRegistrationRepository)
                        .successHandler(successHandler)
                        .failureHandler(failureHandler));
        return http.build();
    }

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(AppProperties properties) {
        ClientRegistration google = CommonOAuth2Provider.GOOGLE
                .getBuilder(REGISTRATION_ID)
                .clientId(properties.getGoogle().getClientId().trim())
                .clientSecret(properties.getGoogle().getClientSecret().trim())
                // Matches the redirect URI registered in the Google console:
                // <public-url>/login/oauth2/code/google
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .build();
        return new InMemoryClientRegistrationRepository(google);
    }
}
