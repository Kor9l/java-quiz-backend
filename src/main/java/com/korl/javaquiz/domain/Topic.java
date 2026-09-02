package com.korl.javaquiz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "topics")
public class Topic {

    @Id
    private String id;

    /** Which module the topic belongs to, and therefore whose lists and stats it appears in. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LearningModule module;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "name_ru", nullable = false)
    private String nameRu;

    public String getId() {
        return id;
    }

    public LearningModule getModule() {
        return module;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getNameRu() {
        return nameRu;
    }
}
