package com.korl.javaquiz.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Plain JPQL over the EntityManager, in place of a Spring Data proxy. The method names are the
 * ones the services already called, so the queries they generated stay visible instead of being
 * inferred from an interface at startup.
 */
@ApplicationScoped
public class AppUserRepository {

    @Inject
    EntityManager em;

    public Optional<AppUser> findById(UUID id) {
        return Optional.ofNullable(em.find(AppUser.class, id));
    }

    public List<AppUser> findAll() {
        return em.createQuery("select u from AppUser u", AppUser.class).getResultList();
    }

    public AppUser save(AppUser user) {
        return em.merge(user);
    }

    public Optional<AppUser> findByEmailIgnoreCase(String email) {
        return em.createQuery("select u from AppUser u where upper(u.email) = upper(:email)", AppUser.class)
                .setParameter("email", email)
                .getResultStream()
                .findFirst();
    }

    public Optional<AppUser> findByGoogleId(String googleId) {
        return em.createQuery("select u from AppUser u where u.googleId = :googleId", AppUser.class)
                .setParameter("googleId", googleId)
                .getResultStream()
                .findFirst();
    }

    public boolean existsByEmailIgnoreCase(String email) {
        return em.createQuery("select count(u) from AppUser u where upper(u.email) = upper(:email)", Long.class)
                .setParameter("email", email)
                .getSingleResult() > 0;
    }

    public long countByRole(Role role) {
        return em.createQuery("select count(u) from AppUser u where u.role = :role", Long.class)
                .setParameter("role", role)
                .getSingleResult();
    }
}
