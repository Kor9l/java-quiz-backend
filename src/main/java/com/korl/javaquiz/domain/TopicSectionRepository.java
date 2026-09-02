package com.korl.javaquiz.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class TopicSectionRepository {

    @Inject
    EntityManager em;

    public Optional<TopicSection> findById(TopicSection.Id id) {
        return Optional.ofNullable(em.find(TopicSection.class, id));
    }

    public List<TopicSection> findByIdTopicIdOrderBySortOrderAsc(String topicId) {
        return em.createQuery(
                        "select s from TopicSection s where s.id.topicId = :topicId order by s.sortOrder asc",
                        TopicSection.class)
                .setParameter("topicId", topicId)
                .getResultList();
    }

    /** Module-scoped for the same reason as {@link TopicRepository#findByModuleOrderBySortOrderAsc}. */
    public List<TopicSection> findByModuleOrderBySortOrderAsc(LearningModule module) {
        return em.createQuery(
                        "select s from TopicSection s where s.id.topicId in "
                                + "(select t.id from Topic t where t.module = :module) "
                                + "order by s.sortOrder asc",
                        TopicSection.class)
                .setParameter("module", module)
                .getResultList();
    }
}
