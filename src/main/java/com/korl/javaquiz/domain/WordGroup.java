package com.korl.javaquiz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A named set of words: one imported text, one lesson, one topic the learner collected. */
@Entity
@Table(name = "word_groups")
public class WordGroup {

    @Id
    private UUID id;

    /** Stable handle, so a seeded group stays recognisable across databases. */
    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "group_type", nullable = false)
    private WordGroupType groupType;

    /** The learner a PERSONAL group belongs to; null on a PUBLIC one. */
    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WordGroup() {
    }

    public static WordGroup personal(UUID ownerId, String title, int sortOrder, Instant createdAt) {
        WordGroup group = new WordGroup();
        group.id = UUID.randomUUID();
        group.code = "u-" + group.id;
        group.title = title;
        group.groupType = WordGroupType.PERSONAL;
        group.ownerId = ownerId;
        group.sortOrder = sortOrder;
        group.createdAt = createdAt;
        return group;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public WordGroupType getGroupType() {
        return groupType;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
