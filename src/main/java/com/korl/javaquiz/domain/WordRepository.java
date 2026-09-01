package com.korl.javaquiz.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.Collection;
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

    /** No access check: for callers holding a word id that already passed one. */
    public Optional<Word> findById(UUID wordId) {
        return Optional.ofNullable(em.find(Word.class, wordId));
    }

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

    /** The pool a quiz round draws on: accessible words, narrowed to the chosen groups. */
    public List<Word> findAccessibleInGroups(UUID userId, Collection<UUID> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return findAccessible(userId);
        }
        return em.createQuery(
                        "select w from Word w where " + IN_ACCESSIBLE_GROUP + " and w.groupId in :groupIds "
                                + "order by w.sortOrder, w.text",
                        Word.class)
                .setParameter("userId", userId)
                .setParameter("groupIds", groupIds)
                .getResultList();
    }

    /**
     * Just the translations, for drawing the wrong options of a question. A projection rather
     * than whole rows: a round asks for these on every question and never needs the entities.
     */
    public List<String> findAccessibleTranslations(UUID userId) {
        return em.createQuery("select w.translation from Word w where " + IN_ACCESSIBLE_GROUP, String.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    public List<String> findAccessibleTexts(UUID userId) {
        return em.createQuery("select w.text from Word w where " + IN_ACCESSIBLE_GROUP, String.class)
                .setParameter("userId", userId)
                .getResultList();
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
