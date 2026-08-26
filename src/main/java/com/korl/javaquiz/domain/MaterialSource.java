package com.korl.javaquiz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "material_sources")
@IdClass(MaterialSource.Pk.class)
public class MaterialSource {

    @Id
    @Column(name = "topic_id")
    private String topicId;

    @Id
    @Column(name = "section_id")
    private String sectionId;

    @Id
    @Column(name = "sort_order")
    private int sortOrder;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String url;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "topic_id", referencedColumnName = "topic_id", insertable = false, updatable = false),
            @JoinColumn(name = "section_id", referencedColumnName = "section_id", insertable = false, updatable = false)
    })
    private MaterialSection material;

    public String getTopicId() {
        return topicId;
    }

    public String getSectionId() {
        return sectionId;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public static class Pk implements Serializable {
        private String topicId;
        private String sectionId;
        private int sortOrder;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Pk other)) {
                return false;
            }
            return sortOrder == other.sortOrder
                    && Objects.equals(topicId, other.topicId)
                    && Objects.equals(sectionId, other.sectionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(topicId, sectionId, sortOrder);
        }
    }
}
