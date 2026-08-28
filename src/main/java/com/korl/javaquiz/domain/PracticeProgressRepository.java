package com.korl.javaquiz.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PracticeProgressRepository {

    @Inject
    EntityManager em;

    public Optional<PracticeProgressEntity> findById(PracticeProgressEntity.Id id) {
        return Optional.ofNullable(em.find(PracticeProgressEntity.class, id));
    }

    public PracticeProgressEntity save(PracticeProgressEntity entity) {
        return em.merge(entity);
    }

    public List<PracticeProgressEntity> findByIdUserId(UUID userId) {
        return em.createQuery(
                        "select p from PracticeProgressEntity p where p.id.userId = :userId",
                        PracticeProgressEntity.class)
                .setParameter("userId", userId)
                .getResultList();
    }
}
