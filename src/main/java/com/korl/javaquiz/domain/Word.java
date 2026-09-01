package com.korl.javaquiz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One vocabulary entry, always inside a {@link WordGroup}. */
@Entity
@Table(name = "words")
public class Word {

    @Id
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private String text;

    @Column(nullable = false)
    private String translation;

    private String example;

    /** Just met, and worth showing as such until the learner clears the flag. */
    @Column(name = "is_new", nullable = false)
    private boolean isNew;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "incorrect_count", nullable = false)
    private int incorrectCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Word() {
    }

    public Word(UUID groupId, int sortOrder, String text, String translation, String example,
                boolean isNew, Instant at) {
        this.id = UUID.randomUUID();
        this.groupId = groupId;
        this.sortOrder = sortOrder;
        this.text = text;
        this.translation = translation;
        this.example = example;
        this.isNew = isNew;
        this.createdAt = at;
        this.updatedAt = at;
    }

    public void edit(String text, String translation, String example, boolean isNew, Instant at) {
        this.text = text;
        this.translation = translation;
        this.example = example;
        this.isNew = isNew;
        this.updatedAt = at;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getText() {
        return text;
    }

    public String getTranslation() {
        return translation;
    }

    public String getExample() {
        return example;
    }

    public boolean isNew() {
        return isNew;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public int getIncorrectCount() {
        return incorrectCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
