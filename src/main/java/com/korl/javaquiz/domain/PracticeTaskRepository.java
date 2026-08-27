package com.korl.javaquiz.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PracticeTaskRepository extends JpaRepository<PracticeTask, String> {

    @Override
    @EntityGraph(attributePaths = {"sources"})
    Optional<PracticeTask> findById(String id);

    List<PracticeTask> findByTrackOrderByDifficultyAscSortOrderAsc(String track);

    List<PracticeTask> findByTrackAndDifficultyOrderBySortOrderAsc(String track, Difficulty difficulty);

    @Query("select distinct t.track from PracticeTask t order by t.track")
    List<String> findTracks();

    long countByTopicIdAndSectionId(String topicId, String sectionId);

    /** Which practice tracks drill a study section, so the article can link to them. */
    @Query("select distinct t.track from PracticeTask t where t.topicId = ?1 and t.sectionId = ?2 order by t.track")
    List<String> findTracksForSection(String topicId, String sectionId);
}
