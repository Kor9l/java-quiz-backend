package com.korl.javaquiz.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TopicSectionRepository extends JpaRepository<TopicSection, TopicSection.Id> {

    List<TopicSection> findByIdTopicIdOrderBySortOrderAsc(String topicId);

    List<TopicSection> findAllByOrderBySortOrderAsc();
}
