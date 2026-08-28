package com.korl.javaquiz.security;

import io.quarkus.security.credential.TokenCredential;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

/**
 * Reads the app's own bearer token off the request. Returning no identity leaves the request
 * anonymous, which the HTTP permissions in {@code application.properties} then accept or reject
 * per path — the same split the Spring filter chain drew between the sign-in endpoints and
 * everything else under {@code /api}.
 */
@ApplicationScoped
public class JwtAuthenticationMechanism implements HttpAuthenticationMechanism {

    private static final String PREFIX = "Bearer ";

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        String header = context.request().getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(PREFIX)) {
            return Uni.createFrom().nullItem();
        }
        String token = header.substring(PREFIX.length()).trim();
        if (token.isEmpty()) {
            return Uni.createFrom().nullItem();
        }
        return identityProviderManager.authenticate(
                new TokenAuthenticationRequest(new TokenCredential(token, "bearer")));
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().item(new ChallengeData(401, "WWW-Authenticate", "Bearer"));
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Set.of(TokenAuthenticationRequest.class);
    }
}
