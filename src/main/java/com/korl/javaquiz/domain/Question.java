package com.korl.javaquiz.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "questions")
public class Question {

    @Id
    private String id;

    @Column(name = "topic_id", nullable = false)
    private String topicId;

    @Column(name = "section_id", nullable = false)
    private String sectionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Column(name = "text_en", nullable = false)
    private String textEn;

    @Column(name = "text_ru", nullable = false)
    private String textRu;

    @Column(columnDefinition = "text")
    private String code;

    @Column(name = "explanation_en", nullable = false)
    private String explanationEn;

    @Column(name = "explanation_ru", nullable = false)
    private String explanationRu;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("optionIndex ASC")
    @BatchSize(size = 256)
    private List<QuestionOption> options = new ArrayList<>();

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    @BatchSize(size = 256)
    private List<QuestionSource> sources = new ArrayList<>();

    public String getId() {
        return id;
    }

    public String getTopicId() {
        return topicId;
    }

    public String getSectionId() {
        return sectionId;
    }

    public String sectionKey() {
        return topicId + "/" + sectionId;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public String getTextEn() {
        return textEn;
    }

    public String getTextRu() {
        return textRu;
    }

    public String getCode() {
        return code;
    }

    public String getExplanationEn() {
        return explanationEn;
    }

    public String getExplanationRu() {
        return explanationRu;
    }

    public List<QuestionOption> getOptions() {
        return options;
    }

    public List<QuestionSource> getSources() {
        return sources;
    }
}
