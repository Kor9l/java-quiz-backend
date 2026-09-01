package com.korl.javaquiz.domain;

import com.korl.javaquiz.userstate.WordStatsPayload;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "word_stats")
public class WordStatsEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "total_answered", nullable = false)
    private int totalAnswered;

    @Column(name = "total_correct", nullable = false)
    private int totalCorrect;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private WordStatsPayload payload = new WordStatsPayload();

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public int getTotalAnswered() {
        return totalAnswered;
    }

    public int getTotalCorrect() {
        return totalCorrect;
    }

    public WordStatsPayload getPayload() {
        return payload;
    }

    /** Keeps the two summary columns in step with the payload, as the backend stats row does. */
    public void setPayload(WordStatsPayload payload) {
        this.payload = payload;
        this.totalAnswered = payload.totalAnswered;
        this.totalCorrect = payload.totalCorrect;
    }
}
