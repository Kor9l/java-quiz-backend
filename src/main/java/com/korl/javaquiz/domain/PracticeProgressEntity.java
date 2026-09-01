package com.korl.javaquiz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** What one user has done with one practice task. */
@Entity
@Table(name = "practice_progress")
public class PracticeProgressEntity {

    @EmbeddedId
    private Id id;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private boolean solved;

    @Column(name = "solved_at")
    private Instant solvedAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    /**
      * The last thing they submitted, so reopening a task restores their work. The column keeps
      * its SQL-era name: the Java track stores its source here too, and renaming it would move
      * a deployed column for nothing.
      */
    @Column(name = "last_sql")
    private String lastSql;

    protected PracticeProgressEntity() {
    }

    public PracticeProgressEntity(UUID userId, String taskId) {
        this.id = new Id(userId, taskId);
    }

    public Id getId() {
        return id;
    }

    public String taskId() {
        return id.taskId;
    }

    public int getAttempts() {
        return attempts;
    }

    public boolean isSolved() {
        return solved;
    }

    public Instant getSolvedAt() {
        return solvedAt;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public String getLastSql() {
        return lastSql;
    }

    /** The same value under a name that does not claim a track. */
    public String getLastSubmission() {
        return lastSql;
    }

    /** Records one graded submission. The solved flag and its timestamp only ever latch on. */
    public void record(String submission, boolean passed, Instant at) {
        attempts++;
        lastSql = submission;
        lastAttemptAt = at;
        if (passed && !solved) {
            solved = true;
            solvedAt = at;
        }
    }

    @Embeddable
    public static class Id implements Serializable {

        @Column(name = "user_id")
        private UUID userId;

        @Column(name = "task_id")
        private String taskId;

        public Id() {
        }

        public Id(UUID userId, String taskId) {
            this.userId = userId;
            this.taskId = taskId;
        }

        public UUID getUserId() {
            return userId;
        }

        public String getTaskId() {
            return taskId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Id other)) {
                return false;
            }
            return Objects.equals(userId, other.userId) && Objects.equals(taskId, other.taskId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, taskId);
        }
    }
}
