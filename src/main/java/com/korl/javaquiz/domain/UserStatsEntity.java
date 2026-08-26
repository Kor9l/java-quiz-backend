package com.korl.javaquiz.domain;

import com.korl.javaquiz.userstate.StatsPayload;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "user_stats")
public class UserStatsEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "total_answered", nullable = false)
    private int totalAnswered;

    @Column(name = "total_correct", nullable = false)
    private int totalCorrect;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private StatsPayload payload = new StatsPayload();

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public int getTotalAnswered() {
        return totalAnswered;
    }

    public void setTotalAnswered(int totalAnswered) {
        this.totalAnswered = totalAnswered;
    }

    public int getTotalCorrect() {
        return totalCorrect;
    }

    public void setTotalCorrect(int totalCorrect) {
        this.totalCorrect = totalCorrect;
    }

    public StatsPayload getPayload() {
        return payload;
    }

    public void setPayload(StatsPayload payload) {
        this.payload = payload;
        this.totalAnswered = payload.totalAnswered;
        this.totalCorrect = payload.totalCorrect;
    }
}
