package com.korl.javaquiz.security;

import com.korl.javaquiz.domain.AppUser;
import com.korl.javaquiz.domain.Role;

import java.security.Principal;
import java.util.UUID;

/** The authenticated caller, carried as the {@link Principal} of the Quarkus SecurityIdentity. */
public class UserPrincipal implements Principal {

    private final UUID id;
    private final String email;
    private final Role role;

    public UserPrincipal(UUID id, String email, Role role) {
        this.id = id;
        this.email = email;
        this.role = role;
    }

    public static UserPrincipal from(AppUser user) {
        return new UserPrincipal(user.getId(), user.getEmail(), user.getRole());
    }

    public UUID getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    @Override
    public String getName() {
        return email;
    }
}
