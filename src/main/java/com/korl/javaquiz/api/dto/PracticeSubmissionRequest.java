package com.korl.javaquiz.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;

/**
 * What a learner submitted for one practice task.
 *
 * <p>Two fields for one value, because the two tracks name it differently and the SQL one was
 * here first: {@code sql} is what the frontend has always sent and keeps sending, and
 * {@code code} is what the Java track sends. Either is accepted on either track — the task
 * decides how its submission is graded, so there is nothing to gain from rejecting the wrong
 * field name.
 */
public class PracticeSubmissionRequest {

    public String sql;

    public String code;

    @JsonIgnore
    public String submission() {
        return code == null || code.isBlank() ? sql : code;
    }

    @JsonIgnore
    @AssertTrue(message = "A submission must not be empty")
    public boolean isSubmitted() {
        String submission = submission();
        return submission != null && !submission.isBlank();
    }
}
