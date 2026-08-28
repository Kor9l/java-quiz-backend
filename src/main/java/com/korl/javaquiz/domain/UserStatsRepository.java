package com.korl.javaquiz.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserStatsRepository {

    @Inject
    EntityManager em;

    public Optional<UserStatsEntity> findById(UUID userId) {
        return Optional.ofNullable(em.find(UserStatsEntity.class, userId));
    }

    public UserStatsEntity save(UserStatsEntity entity) {
        return em.merge(entity);
    }
}
