package com.korl.javaquiz.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class WordGroupRepository {

    /**
     * What a learner is allowed to see: the shared vocabulary plus their own. Written once here
     * and reused by {@link WordRepository}, so the two can never drift into disagreeing about
     * which words a group answer contains.
     */
    static final String ACCESSIBLE =
            "(g.groupType = com.korl.javaquiz.domain.WordGroupType.PUBLIC or g.ownerId = :userId)";

    @Inject
    EntityManager em;

    public List<WordGroup> findAccessible(UUID userId) {
        return em.createQuery(
                        "select g from WordGroup g where " + ACCESSIBLE + " order by g.sortOrder, lower(g.title)",
                        WordGroup.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    public Optional<WordGroup> findAccessibleById(UUID userId, UUID groupId) {
        return em.createQuery(
                        "select g from WordGroup g where g.id = :groupId and " + ACCESSIBLE, WordGroup.class)
                .setParameter("groupId", groupId)
                .setParameter("userId", userId)
                .getResultStream()
                .findFirst();
    }

    /** No access check: for callers that already established the caller may see this group. */
    public Optional<WordGroup> findById(UUID groupId) {
        return Optional.ofNullable(em.find(WordGroup.class, groupId));
    }

    public WordGroup save(WordGroup group) {
        return em.merge(group);
    }

    public void delete(WordGroup group) {
        em.remove(em.contains(group) ? group : em.merge(group));
    }

    public long countAccessible(UUID userId) {
        return em.createQuery("select count(g) from WordGroup g where " + ACCESSIBLE, Long.class)
                .setParameter("userId", userId)
                .getSingleResult();
    }

    /** New groups land after everything already there, so the newest is always last. */
    public int nextSortOrder() {
        Integer max = em.createQuery("select max(g.sortOrder) from WordGroup g", Integer.class).getSingleResult();
        return max == null ? 0 : max + 1;
    }
}
