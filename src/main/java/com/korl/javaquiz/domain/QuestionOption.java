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
@Table(name = "question_options")
@IdClass(QuestionOption.Pk.class)
public class QuestionOption {

    @Id
    @Column(name = "question_id")
    private String questionId;

    @Id
    @Column(name = "option_index")
    private int optionIndex;

    @Column(name = "text_en", nullable = false)
    private String textEn;

    @Column(name = "text_ru", nullable = false)
    private String textRu;

    @Column(nullable = false)
    private boolean correct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", insertable = false, updatable = false)
    private Question question;

    public String getQuestionId() {
        return questionId;
    }

    public int getOptionIndex() {
        return optionIndex;
    }

    public String getTextEn() {
        return textEn;
    }

    public String getTextRu() {
        return textRu;
    }

    public boolean isCorrect() {
        return correct;
    }

    public static class Pk implements Serializable {
        private String questionId;
        private int optionIndex;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Pk other)) {
                return false;
            }
            return optionIndex == other.optionIndex && Objects.equals(questionId, other.questionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(questionId, optionIndex);
        }
    }
}
