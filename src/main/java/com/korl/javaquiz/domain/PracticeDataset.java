package com.korl.javaquiz.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * The small schema a group of practice tasks is posed against, stored as the statements that
 * build it. Nothing is materialised here — the statements are replayed into a fresh in-memory
 * database whenever a submission needs grading.
 */
@Entity
@Table(name = "practice_datasets")
public class PracticeDataset {

    @Id
    private String id;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "title_en", nullable = false)
    private String titleEn;

    @Column(name = "title_ru", nullable = false)
    private String titleRu;

    @Column(name = "description_en", nullable = false)
    private String descriptionEn;

    @Column(name = "description_ru", nullable = false)
    private String descriptionRu;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "practice_dataset_statements",
            joinColumns = @JoinColumn(name = "dataset_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "sql_text", nullable = false)
    private List<String> setupStatements = new ArrayList<>();

    public String getId() {
        return id;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getTitleEn() {
        return titleEn;
    }

    public String getTitleRu() {
        return titleRu;
    }

    public String getDescriptionEn() {
        return descriptionEn;
    }

    public String getDescriptionRu() {
        return descriptionRu;
    }

    public List<String> getSetupStatements() {
        return setupStatements;
    }
}
