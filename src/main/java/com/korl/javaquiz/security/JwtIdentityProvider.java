package com.korl.javaquiz.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

/**
 * Turns a bearer token into an identity. The role is read from the database rather than from the
 * token's {@code role} claim on purpose: a token lives for a day, so trusting the claim would let
 * a demoted admin keep admin rights until it expired.
 */
@ApplicationScoped
public class JwtIdentityProvider implements IdentityProvider<TokenAuthenticationRequest> {

    private final JwtService jwtService;
    private final AuthenticatedUserLookup users;

    public JwtIdentityProvider(JwtService jwtService, AuthenticatedUserLookup users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    public Class<TokenAuthenticationRequest> getRequestType() {
        return TokenAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(TokenAuthenticationRequest request, AuthenticationRequestContext context) {
        // The lookup hits the database, so it must not run on the event loop.
        return context.runBlocking(() -> identity(request.getToken().getToken()));
    }

    private SecurityIdentity identity(String token) {
        UUID userId;
        try {
            Claims claims = jwtService.parse(token);
            userId = UUID.fromString(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new AuthenticationFailedException("Invalid token");
        }
        UserPrincipal principal = users.byId(userId)
                .map(UserPrincipal::from)
                .orElseThrow(() -> new AuthenticationFailedException("Unknown user"));
        return QuarkusSecurityIdentity.builder()
                .setPrincipal(principal)
                .addRole(principal.getRole().name())
                .build();
    }
}
