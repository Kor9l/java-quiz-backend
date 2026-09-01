package com.korl.javaquiz.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Starred words, per learner. Native SQL rather than an entity: the table is a pure join with
 * no fields of its own to map, and every use here is a set membership test.
 */
@ApplicationScoped
public class WordFavoriteRepository {

    @Inject
    EntityManager em;

    @SuppressWarnings("unchecked")
    public Set<UUID> findWordIds(UUID userId) {
        List<UUID> ids = em.createNativeQuery("select word_id from word_favorites where user_id = :userId")
                .setParameter("userId", userId)
                .getResultList();
        return new HashSet<>(ids);
    }

    public void add(UUID userId, UUID wordId) {
        em.createNativeQuery("insert into word_favorites (user_id, word_id) values (:userId, :wordId) "
                        + "on conflict do nothing")
                .setParameter("userId", userId)
                .setParameter("wordId", wordId)
                .executeUpdate();
    }

    public void remove(UUID userId, UUID wordId) {
        em.createNativeQuery("delete from word_favorites where user_id = :userId and word_id = :wordId")
                .setParameter("userId", userId)
                .setParameter("wordId", wordId)
                .executeUpdate();
    }

    public boolean contains(UUID userId, UUID wordId) {
        Number count = (Number) em.createNativeQuery(
                        "select count(*) from word_favorites where user_id = :userId and word_id = :wordId")
                .setParameter("userId", userId)
                .setParameter("wordId", wordId)
                .getSingleResult();
        return count.longValue() > 0;
    }
}
