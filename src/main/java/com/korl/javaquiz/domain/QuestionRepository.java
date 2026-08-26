package com.korl.javaquiz.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, String> {

    @Override
    @EntityGraph(attributePaths = {"options", "sources"})
    Optional<Question> findById(String id);

    @EntityGraph(attributePaths = {"options", "sources"})
    List<Question> findByTopicIdIn(Collection<String> topicIds);

    @EntityGraph(attributePaths = {"options", "sources"})
    List<Question> findByTopicIdAndSectionId(String topicId, String sectionId);

    long countByTopicId(String topicId);

    long countByTopicIdAndSectionId(String topicId, String sectionId);
}
