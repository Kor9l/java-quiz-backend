package com.korl.javaquiz.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, String> {

    @Override
    // Only one List can be join-fetched per query (MultipleBagFetchException);
    // sources come from the @BatchSize select on Question.
    @EntityGraph(attributePaths = {"options"})
    Optional<Question> findById(String id);

    // Only one List can be join-fetched per query (MultipleBagFetchException);
    // sources come from the @BatchSize select on Question.
    @EntityGraph(attributePaths = {"options"})
    List<Question> findByTopicIdIn(Collection<String> topicIds);

    // Only one List can be join-fetched per query (MultipleBagFetchException);
    // sources come from the @BatchSize select on Question.
    @EntityGraph(attributePaths = {"options"})
    List<Question> findByTopicIdAndSectionId(String topicId, String sectionId);

    long countByTopicId(String topicId);

    long countByTopicIdAndSectionId(String topicId, String sectionId);
}
