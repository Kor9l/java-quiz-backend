package com.korl.javaquiz.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserProgressRepository {

    @Inject
    EntityManager em;

    public Optional<UserProgressEntity> findById(UUID userId) {
        return Optional.ofNullable(em.find(UserProgressEntity.class, userId));
    }

    public UserProgressEntity save(UserProgressEntity entity) {
        return em.merge(entity);
    }
}
