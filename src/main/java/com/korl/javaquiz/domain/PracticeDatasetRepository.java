package com.korl.javaquiz.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PracticeDatasetRepository extends JpaRepository<PracticeDataset, String> {

    @Override
    @EntityGraph(attributePaths = {"setupStatements"})
    Optional<PracticeDataset> findById(String id);
}
