package com.korl.javaquiz.practice;

/**
 * Resource ceilings applied to every submission.
 *
 * @param queryTimeoutSeconds how long a statement may run before it is cancelled
 * @param maxRows             rows captured from a result set; anything beyond is truncated
 * @param maxSqlLength        longest submission accepted at all
 * @param previewRows         rows shown to the learner when displaying a result table
 */
public record SandboxLimits(int queryTimeoutSeconds, int maxRows, int maxSqlLength, int previewRows) {

    public static SandboxLimits defaults() {
        return new SandboxLimits(5, 500, 4000, 50);
    }
}
