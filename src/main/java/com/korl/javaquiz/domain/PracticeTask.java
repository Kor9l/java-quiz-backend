package com.korl.javaquiz.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * One hands-on exercise, on either track. Correctness is defined by the reference answer —
 * {@link #getSolutionSql()} on the SQL track, {@link #getSolutionCode()} on the Java one:
 * whatever the reference produces is what a submission has to produce, however it gets there.
 *
 * <p>Which half of the columns is filled follows from {@link #getTrack()}, and the two halves
 * are exclusive. Sharing the table is what lets tracks, difficulties, progress, sources and
 * the link back to the study material be written once instead of once per track.
 */
@Entity
@Table(name = "practice_tasks")
public class PracticeTask {

    @Id
    private String id;

    /** Which practice track this belongs to: {@code sql} or {@code java}. */
    @Column(nullable = false)
    private String track;

    /** The dataset queried. SQL track only. */
    @Column(name = "dataset_id")
    private String datasetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Study material section this exercise drills, so learners can go and read about it. */
    @Column(name = "topic_id")
    private String topicId;

    @Column(name = "section_id")
    private String sectionId;

    @Column(name = "title_en", nullable = false)
    private String titleEn;

    @Column(name = "title_ru", nullable = false)
    private String titleRu;

    @Column(name = "statement_en", nullable = false)
    private String statementEn;

    @Column(name = "statement_ru", nullable = false)
    private String statementRu;

    @Column(name = "hint_en")
    private String hintEn;

    @Column(name = "hint_ru")
    private String hintRu;

    /** Prefilled into the editor to get the learner past the blank page. SQL track only. */
    @Column(name = "starter_sql")
    private String starterSql;

    @Column(name = "solution_sql")
    private String solutionSql;

    /**
     * True when the statement asked for a specific row order, making it part of the answer.
     * SQL track only: on the Java track the cases are always compared in order.
     */
    @Column(name = "order_matters", nullable = false)
    private boolean orderMatters;

    /** The top-level class a submission has to declare. Java track only. */
    @Column(name = "class_name")
    private String className;

    @Column(name = "starter_code")
    private String starterCode;

    @Column(name = "solution_code")
    private String solutionCode;

    @Column(name = "explanation_en", nullable = false)
    private String explanationEn;

    @Column(name = "explanation_ru", nullable = false)
    private String explanationRu;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "practice_task_sources", joinColumns = @JoinColumn(name = "task_id"))
    @OrderColumn(name = "sort_order")
    private List<Source> sources = new ArrayList<>();

    /** The calls made against a submission, and the whole of what is graded. Java track only. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "practice_task_cases", joinColumns = @JoinColumn(name = "task_id"))
    @OrderColumn(name = "sort_order")
    private List<Case> cases = new ArrayList<>();

    public String getId() {
        return id;
    }

    public String getTrack() {
        return track;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getTopicId() {
        return topicId;
    }

    public String getSectionId() {
        return sectionId;
    }

    public String getTitleEn() {
        return titleEn;
    }

    public String getTitleRu() {
        return titleRu;
    }

    public String getStatementEn() {
        return statementEn;
    }

    public String getStatementRu() {
        return statementRu;
    }

    public String getHintEn() {
        return hintEn;
    }

    public String getHintRu() {
        return hintRu;
    }

    public String getStarterSql() {
        return starterSql;
    }

    public String getSolutionSql() {
        return solutionSql;
    }

    public boolean isOrderMatters() {
        return orderMatters;
    }

    public String getExplanationEn() {
        return explanationEn;
    }

    public String getExplanationRu() {
        return explanationRu;
    }

    public String getClassName() {
        return className;
    }

    public String getStarterCode() {
        return starterCode;
    }

    public String getSolutionCode() {
        return solutionCode;
    }

    public List<Source> getSources() {
        return sources;
    }

    public List<Case> getCases() {
        return cases;
    }

    /**
     * One call made against a submission on the Java track. The expression is compiled into a
     * generated harness, so it is bundled content and never anything a user supplied.
     */
    @Embeddable
    public static class Case {

        @Column(nullable = false)
        private String label;

        @Column(nullable = false)
        private String expression;

        public String getLabel() {
            return label;
        }

        public String getExpression() {
            return expression;
        }
    }

    @Embeddable
    public static class Source {

        @Column(nullable = false)
        private String title;

        @Column(nullable = false)
        private String url;

        public String getTitle() {
            return title;
        }

        public String getUrl() {
            return url;
        }
    }
}
