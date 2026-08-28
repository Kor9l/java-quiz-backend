package com.korl.javaquiz.practice;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Grades SQL submissions: policy check, then syntax, then execution against a throwaway
 * database, then a comparison with the reference solution's result.
 *
 * <p>Only the last stage decides correctness, which is the point — two learners can reach the
 * same rows through a join, a subquery or a window function and both are right.
 */
@Component
public class SqlPracticeEngine {

    /** Concurrent sandboxes allowed process-wide; each one is a live in-memory database. */
    private static final int MAX_CONCURRENT_SANDBOXES = 8;

    /** How long a request waits for a sandbox slot before giving up. */
    private static final long SLOT_WAIT_SECONDS = 10;

    private final SandboxLimits limits;
    private final Semaphore slots = new Semaphore(MAX_CONCURRENT_SANDBOXES);

    /**
     * Reference results keyed by task id. The dataset and the solution are static content, so
     * the right answer never changes while the process is up and is worth computing once.
     */
    private final Map<String, ResultTable> expectedResults = new ConcurrentHashMap<>();

    /** Dataset shapes, keyed by dataset id. Static content, same reasoning as above. */
    private final Map<String, SchemaInfo> schemas = new ConcurrentHashMap<>();

    public SqlPracticeEngine(SandboxLimits limits) {
        this.limits = limits;
    }

    public SandboxLimits limits() {
        return limits;
    }

    /** The tables and columns of a dataset, for showing learners what they have to work with. */
    public SchemaInfo describeSchema(String datasetId, List<String> setupStatements) {
        SchemaInfo cached = schemas.get(datasetId);
        if (cached != null) {
            return cached;
        }
        TaskSpec probe = new TaskSpec("schema:" + datasetId, setupStatements, null, false);
        SchemaInfo described = withSandbox(probe, SqlSandbox::describeSchema);
        schemas.putIfAbsent(datasetId, described);
        return described;
    }

    /** The reference result for a task, for showing learners what they are aiming at. */
    public ResultTable expectedResult(TaskSpec task) {
        ResultTable cached = expectedResults.get(task.id());
        if (cached != null) {
            return cached;
        }
        return withSandbox(task, sandbox -> reference(sandbox, task));
    }

    /**
     * Parses a submission without running it. Reports unknown tables and columns too, since
     * H2 resolves them at prepare time.
     */
    public SubmissionOutcome checkSyntax(TaskSpec task, String sql) {
        long started = System.nanoTime();
        SqlGuard.check(sql, limits.maxSqlLength());
        return withSandbox(task, sandbox -> {
            sandbox.checkSyntax(sql);
            return new SubmissionOutcome(
                    SubmissionStatus.PASSED, null, null, null, null, null, elapsedMs(started));
        });
    }

    /** Runs a submission and compares its rows with the reference solution's. */
    public SubmissionOutcome grade(TaskSpec task, String sql) {
        long started = System.nanoTime();
        SqlGuard.check(sql, limits.maxSqlLength());
        return withSandbox(task, sandbox -> {
            ResultTable expected = reference(sandbox, task);
            sandbox.checkSyntax(sql);
            ResultTable actual = sandbox.runSubmission(sql);
            ResultComparator.Comparison comparison =
                    ResultComparator.compare(expected, actual, task.orderMatters());
            return new SubmissionOutcome(
                    comparison.matched() ? SubmissionStatus.PASSED : SubmissionStatus.WRONG_RESULT,
                    comparison.reasonKey(),
                    null,
                    actual.preview(limits.previewRows()),
                    expected.preview(limits.previewRows()),
                    comparison,
                    elapsedMs(started));
        });
    }

    private ResultTable reference(SqlSandbox sandbox, TaskSpec task) {
        ResultTable cached = expectedResults.get(task.id());
        if (cached != null) {
            return cached;
        }
        ResultTable computed = sandbox.runReference(task.solutionSql());
        expectedResults.putIfAbsent(task.id(), computed);
        return computed;
    }

    /** Forgets everything cached, so reloaded content is picked up. */
    public void clearCache() {
        expectedResults.clear();
        schemas.clear();
    }

    private <T> T withSandbox(TaskSpec task, SandboxWork<T> work) {
        boolean acquired;
        try {
            acquired = slots.tryAcquire(SLOT_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SqlSubmissionException(SubmissionStatus.RUNTIME_ERROR, "practice.error.busy", null);
        }
        if (!acquired) {
            throw new SqlSubmissionException(SubmissionStatus.RUNTIME_ERROR, "practice.error.busy", null);
        }
        try (SqlSandbox sandbox = SqlSandbox.create(task.setupStatements(), limits)) {
            return work.apply(sandbox);
        } finally {
            slots.release();
        }
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    @FunctionalInterface
    private interface SandboxWork<T> {
        T apply(SqlSandbox sandbox);
    }
}
