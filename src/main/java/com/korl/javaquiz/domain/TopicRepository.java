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

    public List<Topic> findAllByOrderBySortOrderAsc() {
        return em.createQuery("select t from Topic t order by t.sortOrder asc", Topic.class).getResultList();
    }
}
