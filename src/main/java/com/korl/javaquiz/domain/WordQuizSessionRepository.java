package com.korl.javaquiz.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class WordQuizSessionRepository {

    @Inject
    EntityManager em;

    public Optional<WordQuizSessionEntity> findById(UUID id) {
        return Optional.ofNullable(em.find(WordQuizSessionEntity.class, id));
    }

    public WordQuizSessionEntity save(WordQuizSessionEntity session) {
        return em.merge(session);
    }

    public Optional<WordQuizSessionEntity> findActive(UUID userId) {
        return em.createQuery(
                        "select s from WordQuizSessionEntity s where s.userId = :userId and s.finished = false "
                                + "order by s.startedAt desc",
                        WordQuizSessionEntity.class)
                .setParameter("userId", userId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }
}
