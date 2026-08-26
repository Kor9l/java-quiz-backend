package com.korl.javaquiz.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MaterialSectionRepository extends JpaRepository<MaterialSection, TopicSection.Id> {

    @EntityGraph(attributePaths = "sources")
    Optional<MaterialSection> findWithSourcesById(TopicSection.Id id);
}
