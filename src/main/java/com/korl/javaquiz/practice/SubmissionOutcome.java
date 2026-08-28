package com.korl.javaquiz.practice;

/**
 * The graded verdict for one submission.
 *
 * @param status     how far the submission got
 * @param messageKey i18n key describing the failure, null when it passed
 * @param detail     verbatim engine message, null when there was none
 * @param result     what the submission produced, null when it never ran
 * @param expected   the reference result, so the learner can see the target
 * @param comparison how the two differ, null when the submission never ran
 * @param durationMs wall-clock time spent building the sandbox and running the statement
 */
public record SubmissionOutcome(
        SubmissionStatus status,
        String messageKey,
        String detail,
        ResultTable result,
        ResultTable expected,
        ResultComparator.Comparison comparison,
        long durationMs) {

    public boolean passed() {
        return status.passed();
    }
}
