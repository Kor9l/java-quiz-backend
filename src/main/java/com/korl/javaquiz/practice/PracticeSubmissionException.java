package com.korl.javaquiz.practice;

/**
 * A submission that could not be graded, on either track. Carries a message key rather than
 * prose so the frontend can render the reason in the user's language; {@link #getDetail()}
 * holds the raw engine text, which is shown verbatim because it is the most useful part for
 * a learner.
 */
public class PracticeSubmissionException extends RuntimeException {

    private final SubmissionStatus status;
    private final String messageKey;
    private final String detail;

    public PracticeSubmissionException(SubmissionStatus status, String messageKey, String detail) {
        super(messageKey + (detail == null ? "" : ": " + detail));
        this.status = status;
        this.messageKey = messageKey;
        this.detail = detail;
    }

    public SubmissionStatus getStatus() {
        return status;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getDetail() {
        return detail;
    }
}
