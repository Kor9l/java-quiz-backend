package com.korl.javaquiz.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;

import java.util.Optional;

@ApplicationScoped
public class PracticeDatasetRepository {

    @Inject
    EntityManager em;

    /**
     * The setup statements are the dataset as far as callers are concerned — the sandbox
     * replays them on every attempt — so they are initialised before the entity is handed out.
     */
    public Optional<PracticeDataset> findById(String id) {
        PracticeDataset dataset = em.find(PracticeDataset.class, id);
        if (dataset != null) {
            Hibernate.initialize(dataset.getSetupStatements());
        }
        return Optional.ofNullable(dataset);
    }
}
