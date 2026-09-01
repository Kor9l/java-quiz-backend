package com.korl.javaquiz.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class WordStatsRepository {

    @Inject
    EntityManager em;

    public Optional<WordStatsEntity> findById(UUID userId) {
        return Optional.ofNullable(em.find(WordStatsEntity.class, userId));
    }

    public WordStatsEntity save(WordStatsEntity entity) {
        return em.merge(entity);
    }
}
