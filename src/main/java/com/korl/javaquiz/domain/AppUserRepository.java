package com.korl.javaquiz.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    Optional<AppUser> findByGoogleId(String googleId);

    boolean existsByEmailIgnoreCase(String email);

    long countByRole(Role role);
}
