package com.korl.javaquiz.practice;

/** Outcome of a practice submission, reported to the client as-is. */
public enum SubmissionStatus {

    /** The result set matched the expected one. */
    PASSED,
    /** The statement ran, but produced different rows. */
    WRONG_RESULT,
    /** Rejected before execution: not a single read-only query. */
    POLICY_ERROR,
    /** The parser refused the statement, or it referenced something that does not exist. */
    SYNTAX_ERROR,
    /** The statement parsed but blew up while running. */
    RUNTIME_ERROR,
    /** The statement was still running when the time limit expired. */
    TIMEOUT;

    public boolean passed() {
        return this == PASSED;
    }
}
