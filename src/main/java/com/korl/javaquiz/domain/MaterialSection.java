package com.korl.javaquiz.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "material_sections")
public class MaterialSection {

    @EmbeddedId
    @AttributeOverrides({
            @AttributeOverride(name = "topicId", column = @Column(name = "topic_id")),
            @AttributeOverride(name = "id", column = @Column(name = "section_id"))
    })
    private TopicSection.Id id;

    @Column(name = "estimated_minutes", nullable = false)
    private int estimatedMinutes;

    @Column(name = "summary_en", nullable = false)
    private String summaryEn;

    @Column(name = "summary_ru", nullable = false)
    private String summaryRu;

    @Column(name = "body_en", nullable = false)
    private String bodyEn;

    @Column(name = "body_ru", nullable = false)
    private String bodyRu;

    @OneToMany(mappedBy = "material", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<MaterialSource> sources = new ArrayList<>();

    public TopicSection.Id getId() {
        return id;
    }

    public String topicId() {
        return id.getTopicId();
    }

    public String sectionId() {
        return id.getId();
    }

    public int getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public String getSummaryEn() {
        return summaryEn;
    }

    public String getSummaryRu() {
        return summaryRu;
    }

    public String getBodyEn() {
        return bodyEn;
    }

    public String getBodyRu() {
        return bodyRu;
    }

    public List<MaterialSource> getSources() {
        return sources;
    }
}
