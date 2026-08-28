package com.korl.javaquiz.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserSettingsRepository {

    @Inject
    EntityManager em;

    public Optional<UserSettingsEntity> findById(UUID userId) {
        return Optional.ofNullable(em.find(UserSettingsEntity.class, userId));
    }

    public UserSettingsEntity save(UserSettingsEntity entity) {
        return em.merge(entity);
    }
}
