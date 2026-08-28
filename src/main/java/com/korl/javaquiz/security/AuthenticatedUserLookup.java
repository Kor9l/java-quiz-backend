package com.korl.javaquiz.security;

import com.korl.javaquiz.domain.AppUser;
import com.korl.javaquiz.domain.AppUserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * A separate bean only so the transaction interceptor applies: {@link JwtIdentityProvider} calls
 * this from a worker thread that has no session of its own yet.
 */
@ApplicationScoped
public class AuthenticatedUserLookup {

    private final AppUserRepository users;

    public AuthenticatedUserLookup(AppUserRepository users) {
        this.users = users;
    }

    @Transactional
    public Optional<AppUser> byId(UUID id) {
        return users.findById(id);
    }
}
