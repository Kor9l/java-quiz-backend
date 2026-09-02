package com.korl.javaquiz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "sections")
public class TopicSection {

    @EmbeddedId
    private Id id;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "title_en", nullable = false)
    private String titleEn;

    @Column(name = "title_ru", nullable = false)
    private String titleRu;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Level level;

    /**
     * The area of grammar the section drills — tenses, articles, modals, syntax. Null for
     * backend sections, which are already grouped by the topic they sit in.
     *
     * <p>A grammar course runs one level end to end, in teaching order, so the level is what
     * the courses are cut by. This is the other cut — every conditional across all three
     * levels — kept possible without moving content later.
     */
    @Column
    private String area;

    public Id getId() {
        return id;
    }

    public String topicId() {
        return id.topicId;
    }

    public String sectionId() {
        return id.id;
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

    public Level getLevel() {
        return level;
    }

    public String getArea() {
        return area;
    }

    @Embeddable
    public static class Id implements Serializable {
        @Column(name = "topic_id")
        private String topicId;
        @Column(name = "id")
        private String id;

        public Id() {
        }

        public Id(String topicId, String id) {
            this.topicId = topicId;
            this.id = id;
        }

        public String getTopicId() {
            return topicId;
        }

        public String getId() {
            return id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Id other)) {
                return false;
            }
            return Objects.equals(topicId, other.topicId) && Objects.equals(id, other.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(topicId, id);
        }
    }
}
