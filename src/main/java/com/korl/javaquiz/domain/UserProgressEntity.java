package com.korl.javaquiz.domain;

import com.korl.javaquiz.userstate.ProgressPayload;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "user_progress")
public class UserProgressEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private ProgressPayload payload = new ProgressPayload();

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public ProgressPayload getPayload() {
        return payload;
    }

    public void setPayload(ProgressPayload payload) {
        this.payload = payload;
    }
}
