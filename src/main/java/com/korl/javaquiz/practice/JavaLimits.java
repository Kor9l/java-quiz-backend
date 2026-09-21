package com.korl.javaquiz.practice;

/**
 * Resource ceilings applied to every Java submission, alongside the ones
 * {@link SandboxPolicy} applies to what it may name.
 *
 * @param runTimeoutSeconds how long all of a task's cases together may run, the child JVM's own
 *                          start-up included — it is the deadline the process is killed at
 * @param maxSourceLength   longest submission accepted at all
 * @param maxOutputBytes    output captured per attempt; anything beyond is dropped
 * @param heapMegabytes     {@code -Xmx} for the child, so a runaway allocation ends as an
 *                          {@code OutOfMemoryError} inside a process nobody else is using
 */
public record JavaLimits(
        int runTimeoutSeconds, int maxSourceLength, int maxOutputBytes, int heapMegabytes) {

    public static JavaLimits defaults() {
        return new JavaLimits(8, 20_000, 8_000, 64);
    }
}
