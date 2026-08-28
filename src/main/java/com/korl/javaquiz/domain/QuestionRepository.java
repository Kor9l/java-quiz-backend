package com.korl.javaquiz.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class QuestionRepository {

    // Only one List can be join-fetched per query (MultipleBagFetchException);
    // sources come from the @BatchSize select on Question.
    private static final String WITH_OPTIONS = "select q from Question q left join fetch q.options";

    @Inject
    EntityManager em;

    public Optional<Question> findById(String id) {
        return em.createQuery(WITH_OPTIONS + " where q.id = :id", Question.class)
                .setParameter("id", id)
                .getResultStream()
                .findFirst();
    }

    public List<Question> findByTopicIdIn(Collection<String> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery(WITH_OPTIONS + " where q.topicId in :topicIds", Question.class)
                .setParameter("topicIds", topicIds)
                .getResultList();
    }

    public List<Question> findByTopicIdAndSectionId(String topicId, String sectionId) {
        return em.createQuery(
                        WITH_OPTIONS + " where q.topicId = :topicId and q.sectionId = :sectionId", Question.class)
                .setParameter("topicId", topicId)
                .setParameter("sectionId", sectionId)
                .getResultList();
    }

    public long count() {
        return em.createQuery("select count(q) from Question q", Long.class).getSingleResult();
    }

    public long countByTopicId(String topicId) {
        return em.createQuery("select count(q) from Question q where q.topicId = :topicId", Long.class)
                .setParameter("topicId", topicId)
                .getSingleResult();
    }

    public long countByTopicIdAndSectionId(String topicId, String sectionId) {
        return em.createQuery(
                        "select count(q) from Question q where q.topicId = :topicId and q.sectionId = :sectionId",
                        Long.class)
                .setParameter("topicId", topicId)
                .setParameter("sectionId", sectionId)
                .getSingleResult();
    }
}
