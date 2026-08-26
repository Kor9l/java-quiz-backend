package com.korl.javaquiz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "question_sources")
@IdClass(QuestionSource.Pk.class)
public class QuestionSource {

    @Id
    @Column(name = "question_id")
    private String questionId;

    @Id
    @Column(name = "sort_order")
    private int sortOrder;

    @Column(nullable = false)
    private String url;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", insertable = false, updatable = false)
    private Question question;

    public String getQuestionId() {
        return questionId;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getUrl() {
        return url;
    }

    public static class Pk implements Serializable {
        private String questionId;
        private int sortOrder;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Pk other)) {
                return false;
            }
            return sortOrder == other.sortOrder && Objects.equals(questionId, other.questionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(questionId, sortOrder);
        }
    }
}
