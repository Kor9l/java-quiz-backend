package com.korl.javaquiz.practice;

import java.util.List;

/**
 * The graded verdict for one submission, on either track.
 *
 * @param status      how far the submission got
 * @param messageKey  i18n key describing the failure, null when it passed
 * @param detail      verbatim engine message, null when there was none
 * @param result      what the submission produced, null when it never ran
 * @param expected    the reference result, so the learner can see the target
 * @param comparison  how the two differ, null when the submission never ran
 * @param durationMs  wall-clock time spent building the sandbox and running the submission
 * @param diagnostics compiler messages; always empty on the SQL track
 * @param output      what the submission printed, one entry per case; empty on the SQL track
 */
public record SubmissionOutcome(
        SubmissionStatus status,
        String messageKey,
        String detail,
        ResultTable result,
        ResultTable expected,
        ResultComparator.Comparison comparison,
        long durationMs,
        List<CompileDiagnostic> diagnostics,
        List<String> output) {

    /** An outcome from a track that has no compiler and captures no output. */
    public static SubmissionOutcome of(
            SubmissionStatus status,
            String messageKey,
            String detail,
            ResultTable result,
            ResultTable expected,
            ResultComparator.Comparison comparison,
            long durationMs) {
        return new SubmissionOutcome(
                status, messageKey, detail, result, expected, comparison, durationMs, List.of(), List.of());
    }

    public boolean passed() {
        return status.passed();
    }
}
