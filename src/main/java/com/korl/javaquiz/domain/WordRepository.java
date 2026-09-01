package com.korl.javaquiz.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class WordRepository {

    private static final String IN_ACCESSIBLE_GROUP =
            "w.groupId in (select g.id from WordGroup g where " + WordGroupRepository.ACCESSIBLE + ")";

    @Inject
    EntityManager em;

    public Optional<Word> findAccessibleById(UUID userId, UUID wordId) {
        return em.createQuery(
                        "select w from Word w where w.id = :wordId and " + IN_ACCESSIBLE_GROUP, Word.class)
                .setParameter("wordId", wordId)
                .setParameter("userId", userId)
                .getResultStream()
                .findFirst();
    }

    /** Every word the learner may see, in one query — the list screen shows them all at once. */
    public List<Word> findAccessible(UUID userId) {
        return em.createQuery(
                        "select w from Word w where " + IN_ACCESSIBLE_GROUP + " order by w.sortOrder, w.text",
                        Word.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    public List<Word> findByGroupId(UUID groupId) {
        return em.createQuery("select w from Word w where w.groupId = :groupId order by w.sortOrder, w.text",
                        Word.class)
                .setParameter("groupId", groupId)
                .getResultList();
    }

    /** Word counts for the group list, as one grouped query rather than a count per group. */
    public Map<UUID, Long> countByGroup(UUID userId) {
        return em.createQuery(
                        "select w.groupId, count(w) from Word w where " + IN_ACCESSIBLE_GROUP + " group by w.groupId",
                        Object[].class)
                .setParameter("userId", userId)
                .getResultStream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
    }

    public long countAccessible(UUID userId) {
        return em.createQuery("select count(w) from Word w where " + IN_ACCESSIBLE_GROUP, Long.class)
                .setParameter("userId", userId)
                .getSingleResult();
    }

    public Word save(Word word) {
        return em.merge(word);
    }

    public void delete(Word word) {
        em.remove(em.contains(word) ? word : em.merge(word));
    }

    /** Appends to the end of a group, so an import keeps the order it was pasted in. */
    public int nextSortOrder(UUID groupId) {
        Integer max = em.createQuery("select max(w.sortOrder) from Word w where w.groupId = :groupId", Integer.class)
                .setParameter("groupId", groupId)
                .getSingleResult();
        return max == null ? 0 : max + 1;
    }
}
