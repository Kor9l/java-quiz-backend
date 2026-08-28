package com.korl.javaquiz.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.Optional;

@ApplicationScoped
public class MaterialSectionRepository {

    @Inject
    EntityManager em;

    /** Sources are fetched along with the article, which always renders them. */
    public Optional<MaterialSection> findWithSourcesById(TopicSection.Id id) {
        return em.createQuery(
                        "select m from MaterialSection m left join fetch m.sources where m.id = :id",
                        MaterialSection.class)
                .setParameter("id", id)
                .getResultStream()
                .findFirst();
    }
}
