package com.korl.javaquiz.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class QuizSessionRepository {

    @Inject
    EntityManager em;

    public Optional<QuizSessionEntity> findById(UUID id) {
        return Optional.ofNullable(em.find(QuizSessionEntity.class, id));
    }

    public QuizSessionEntity save(QuizSessionEntity session) {
        return em.merge(session);
    }

    public Optional<QuizSessionEntity> findFirstByUserIdAndFinishedFalseOrderByStartedAtDesc(UUID userId) {
        return em.createQuery(
                        "select s from QuizSessionEntity s where s.userId = :userId and s.finished = false "
                                + "order by s.startedAt desc",
                        QuizSessionEntity.class)
                .setParameter("userId", userId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }
}
