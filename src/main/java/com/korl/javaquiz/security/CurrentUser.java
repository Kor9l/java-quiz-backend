package com.korl.javaquiz.security;

import com.korl.javaquiz.api.error.ApiException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response.Status;

import java.util.UUID;

/** The caller behind the current request, in place of Spring's {@code @AuthenticationPrincipal}. */
@ApplicationScoped
public class CurrentUser {

    @Inject
    SecurityIdentity identity;

    public UserPrincipal principal() {
        if (identity.getPrincipal() instanceof UserPrincipal user) {
            return user;
        }
        throw new ApiException(Status.UNAUTHORIZED, "Not authenticated");
    }

    public UUID id() {
        return principal().getId();
    }
}
