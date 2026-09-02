package com.korl.javaquiz.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class TopicRepository {

    @Inject
    EntityManager em;

    public Optional<Topic> findById(String id) {
        return Optional.ofNullable(em.find(Topic.class, id));
    }

    /**
     * Always scoped by module, and there is deliberately no unscoped variant: a list of every
     * topic is never the right answer to any question this app asks, and having one available
     * is how grammar courses would end up in the backend's topic list, its "all topics" quiz
     * and its stats breakdown.
     */
    public List<Topic> findByModuleOrderBySortOrderAsc(LearningModule module) {
        return em.createQuery(
                        "select t from Topic t where t.module = :module order by t.sortOrder asc",
                        Topic.class)
                .setParameter("module", module)
                .getResultList();
    }
}
