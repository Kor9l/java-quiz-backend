package com.korl.javaquiz.practice;

/**
 * Resource ceilings applied to every Java submission, alongside the ones
 * {@link SandboxPolicy} applies to what it may name.
 *
 * @param runTimeoutSeconds how long all of a task's cases together may run
 * @param maxSourceLength   longest submission accepted at all
 * @param maxOutputBytes    output captured per attempt; anything beyond is dropped
 */
public record JavaLimits(int runTimeoutSeconds, int maxSourceLength, int maxOutputBytes) {

    public static JavaLimits defaults() {
        return new JavaLimits(5, 20_000, 8_000);
    }
}
