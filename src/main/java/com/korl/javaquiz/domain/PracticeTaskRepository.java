package com.korl.javaquiz.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PracticeTaskRepository {

    @Inject
    EntityManager em;

    /** Sources come along: every task view lists them. */
    public Optional<PracticeTask> findById(String id) {
        return em.createQuery(
                        "select t from PracticeTask t left join fetch t.sources where t.id = :id", PracticeTask.class)
                .setParameter("id", id)
                .getResultStream()
                .findFirst();
    }

    public List<PracticeTask> findByTrackOrderByDifficultyAscSortOrderAsc(String track) {
        return em.createQuery(
                        "select t from PracticeTask t where t.track = :track "
                                + "order by t.difficulty asc, t.sortOrder asc",
                        PracticeTask.class)
                .setParameter("track", track)
                .getResultList();
    }

    public List<PracticeTask> findByTrackAndDifficultyOrderBySortOrderAsc(String track, Difficulty difficulty) {
        return em.createQuery(
                        "select t from PracticeTask t where t.track = :track and t.difficulty = :difficulty "
                                + "order by t.sortOrder asc",
                        PracticeTask.class)
                .setParameter("track", track)
                .setParameter("difficulty", difficulty)
                .getResultList();
    }

    public List<String> findTracks() {
        return em.createQuery("select distinct t.track from PracticeTask t order by t.track", String.class)
                .getResultList();
    }

    public long countByTopicIdAndSectionId(String topicId, String sectionId) {
        return em.createQuery(
                        "select count(t) from PracticeTask t where t.topicId = :topicId and t.sectionId = :sectionId",
                        Long.class)
                .setParameter("topicId", topicId)
                .setParameter("sectionId", sectionId)
                .getSingleResult();
    }

    /** Which practice tracks drill a study section, so the article can link to them. */
    public List<String> findTracksForSection(String topicId, String sectionId) {
        return em.createQuery(
                        "select distinct t.track from PracticeTask t "
                                + "where t.topicId = :topicId and t.sectionId = :sectionId order by t.track",
                        String.class)
                .setParameter("topicId", topicId)
                .setParameter("sectionId", sectionId)
                .getResultList();
    }
}
