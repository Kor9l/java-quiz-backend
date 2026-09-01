package com.korl.javaquiz.practice;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Grades Java submissions: policy check, then compilation, then a bytecode check, then
 * execution against the task's cases, then a comparison with what the reference solution
 * returned for the same cases.
 *
 * <p>The same shape as {@link SqlPracticeEngine}, and for the same reason: only the last stage
 * decides correctness. A loop, a stream and a recursive call that all return the same values
 * are all right, and the engine has no opinion about which one the learner wrote.
 */
@ApplicationScoped
public class JavaPracticeEngine {

    /**
     * Concurrent compilations allowed process-wide. Lower than the SQL sandbox's eight because
     * a compile is CPU-bound and the deployment target is a tenth of a core: letting several
     * run at once makes all of them slow rather than any of them fast.
     */
    private static final int MAX_CONCURRENT_SANDBOXES = 2;

    /** How long a request waits for a sandbox slot before giving up. */
    private static final long SLOT_WAIT_SECONDS = 15;

    private final JavaLimits limits;
    private final Semaphore slots = new Semaphore(MAX_CONCURRENT_SANDBOXES);

    /**
     * Reference results keyed by task id. The cases and the solution are static content, so the
     * right answer never changes while the process is up and is worth computing once — which
     * matters more here than on the SQL track, since computing it means a compile.
     */
    private final Map<String, JavaSandbox.Execution> expectedResults = new ConcurrentHashMap<>();

    public JavaPracticeEngine(JavaLimits limits) {
        this.limits = limits;
    }

    public JavaLimits limits() {
        return limits;
    }

    /** What the reference solution returns for each case, for showing learners the target. */
    public ResultTable expectedResult(JavaTaskSpec task) {
        return reference(task).results();
    }

    /**
     * Compiles a submission without running it, which is the whole of the answer for a learner
     * who only wants to know whether it builds.
     */
    public SubmissionOutcome checkCompilation(JavaTaskSpec task, String source) {
        long started = System.nanoTime();
        JavaGuard.check(source, task.className(), limits.maxSourceLength());
        SourceCompiler.Result compiled = withSlot(() -> SourceCompiler.compile(List.of(
                new MemorySources.Source(task.className(), source),
                new MemorySources.Source(JavaSandbox.HARNESS_CLASS, JavaSandbox.harnessSource(task)))));
        if (!compiled.succeeded()) {
            return failedToCompile(compiled.diagnostics(), started);
        }
        // The bytecode check belongs here too: a submission that compiles but reaches for
        // something it may not have should hear so now rather than on its first run.
        ClassFileGuard.check(compiled.bytecode(), compiled.bytecode().keySet());
        return new SubmissionOutcome(
                SubmissionStatus.PASSED, null, null, null, null, null,
                elapsedMs(started), compiled.diagnostics(), List.of());
    }

    /** Runs a submission and compares what it returned with what the reference returned. */
    public SubmissionOutcome grade(JavaTaskSpec task, String source) {
        long started = System.nanoTime();
        JavaGuard.check(source, task.className(), limits.maxSourceLength());
        JavaSandbox.Execution expected = reference(task);
        JavaSandbox.Execution actual;
        try {
            actual = withSlot(() -> JavaSandbox.run(task, source, limits));
        } catch (JavaSandbox.CompilationFailure failure) {
            // Not compiling is an ordinary thing for a submission to do, and the diagnostics
            // are the answer the learner asked for — so it is an outcome, not an error.
            return failedToCompile(failure.diagnostics(), started);
        }
        // Order matters on this track and is not a task-level choice: case three is case three
        // whatever it returns, so comparing the cases as an unordered bag would be meaningless.
        ResultComparator.Comparison comparison =
                ResultComparator.compare(expected.results(), actual.results(), true);
        return new SubmissionOutcome(
                comparison.matched() ? SubmissionStatus.PASSED : SubmissionStatus.WRONG_RESULT,
                comparison.reasonKey(),
                null,
                actual.results(),
                expected.results(),
                comparison,
                elapsedMs(started),
                List.of(),
                actual.output());
    }

    /**
      * Turns compiler output into an outcome. Errors that are all in the generated harness get
      * their own message key: the submission compiled, and what failed is the shape it exposes —
      * a missing method, a wrong parameter type, a signature too narrow for what the cases pass.
      * Telling a learner "it does not compile" there would point at code they never wrote.
      */
    private static SubmissionOutcome failedToCompile(List<CompileDiagnostic> diagnostics, long started) {
        boolean inSubmission = diagnostics.stream()
                .anyMatch(diagnostic -> diagnostic.isError() && diagnostic.inSubmission());
        return new SubmissionOutcome(
                SubmissionStatus.COMPILE_ERROR,
                inSubmission ? "practice.error.compile" : "practice.error.signature",
                diagnostics.isEmpty() ? null : diagnostics.get(0).message(),
                null, null, null,
                elapsedMs(started),
                diagnostics,
                List.of());
    }

    /** Forgets everything cached, so reloaded content is picked up. */
    public void clearCache() {
        expectedResults.clear();
    }

    private JavaSandbox.Execution reference(JavaTaskSpec task) {
        JavaSandbox.Execution cached = expectedResults.get(task.id());
        if (cached != null) {
            return cached;
        }
        JavaSandbox.Execution computed;
        try {
            computed = withSlot(() -> JavaSandbox.run(task, task.solutionCode(), limits));
        } catch (JavaSandbox.CompilationFailure failure) {
            // Bundled content, so this is a broken exercise rather than a learner's mistake —
            // and the same failure the build's content test exists to prevent reaching here.
            throw new IllegalStateException(
                    "Reference solution of " + task.id() + " does not compile: " + failure.getDetail(), failure);
        }
        expectedResults.putIfAbsent(task.id(), computed);
        return computed;
    }

    private <T> T withSlot(SandboxWork<T> work) {
        boolean acquired;
        try {
            acquired = slots.tryAcquire(SLOT_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PracticeSubmissionException(SubmissionStatus.RUNTIME_ERROR, "practice.error.busy", null);
        }
        if (!acquired) {
            throw new PracticeSubmissionException(SubmissionStatus.RUNTIME_ERROR, "practice.error.busy", null);
        }
        try {
            return work.get();
        } finally {
            slots.release();
        }
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    @FunctionalInterface
    private interface SandboxWork<T> {
        T get();
    }
}
