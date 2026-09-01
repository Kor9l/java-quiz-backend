package com.korl.javaquiz.practice;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs one submission's compiled classes and collects what each case returned.
 *
 * <p>Everything happens on one throwaway thread with one throwaway class loader, both dropped
 * when the attempt ends. The thread is what the timeout is applied to; the class loader is what
 * keeps the submission away from the application and from the next submission's statics.
 *
 * <p><strong>What a timeout can and cannot do.</strong> A run that overruns is interrupted, and
 * interruption is cooperative — a submission spinning in a tight loop that never blocks does not
 * observe it. Since Java has no safe way to stop such a thread, this abandons it as a daemon and
 * counts it. Once {@link #ABANDONED_LIMIT} threads have been abandoned the sandbox stops
 * accepting work, so the failure mode is a Java track that reports itself busy rather than a
 * process quietly filling up with spinning threads.
 */
final class JavaSandbox {

    /** Class the case expressions are compiled into. Named not to collide with a submission. */
    static final String HARNESS_CLASS = "__PracticeHarness__";

    /** How long a thread is given to notice its interrupt before it is written off. */
    private static final long INTERRUPT_GRACE_MILLIS = 500;

    /** Abandoned threads tolerated process-wide before the track refuses to run anything. */
    private static final int ABANDONED_LIMIT = 4;

    private static final AtomicInteger ABANDONED = new AtomicInteger();

    private JavaSandbox() {
    }

    /** Threads left spinning after a timeout. Zero unless a submission ignored its interrupt. */
    static int abandonedThreads() {
        return ABANDONED.get();
    }

    static boolean isExhausted() {
        return ABANDONED.get() >= ABANDONED_LIMIT;
    }

    /**
     * Compiles a class and its harness, checks the result against the policy, runs every case
     * and returns what they produced.
     *
     * @param source what to compile — a learner's submission or a bundled reference solution
     * @throws PracticeSubmissionException when the source is refused, does not compile, throws,
     *                                     or overruns
     */
    static Execution run(JavaTaskSpec task, String source, JavaLimits limits) {
        SourceCompiler.Result compiled = SourceCompiler.compile(List.of(
                new MemorySources.Source(task.className(), source),
                new MemorySources.Source(HARNESS_CLASS, harnessSource(task))));
        if (!compiled.succeeded()) {
            throw new CompilationFailure(compiled.diagnostics());
        }
        ClassFileGuard.check(compiled.bytecode(), compiled.bytecode().keySet());
        return execute(compiled.bytecode(), task.cases(), limits);
    }

    /**
     * Imports a case expression is written against, so that a case reads
     * {@code Solution.max(List.of(1, 2))} rather than spelling out {@code java.util.List}.
     * Wildcards from the sandbox's own allowlist; nothing here widens what a submission may do,
     * since the submission is a separate compilation unit with its own imports.
     */
    private static final String HARNESS_IMPORTS = """
            import java.util.*;
            import java.util.function.*;
            import java.util.stream.*;
            """;

    /**
     * The generated class the cases are called from. Each case becomes its own method, so a
     * case that throws does not take the rest of them with it, and the harness stays trivial
     * enough that a compile error in it can only mean the case expression is wrong.
     */
    static String harnessSource(JavaTaskSpec task) {
        StringBuilder source = new StringBuilder(HARNESS_IMPORTS)
                .append("\npublic final class ").append(HARNESS_CLASS).append(" {\n")
                .append(ATTEMPT_HELPER);
        List<JavaTaskSpec.Case> cases = task.cases();
        for (int i = 0; i < cases.size(); i++) {
            source.append("\n    public static Object case").append(i).append("() {\n")
                    .append("        return ").append(cases.get(i).expression()).append(";\n")
                    .append("    }\n");
        }
        return source.append("}\n").toString();
    }

    /**
     * Lets a case assert on what a submission <em>throws</em>. Without it a case could only be
     * an expression, and an expression cannot catch — so a task about exceptions would have no
     * way to check that the right one is raised for bad input.
     */
    private static final String ATTEMPT_HELPER = """
                public static Object attempt(Supplier<Object> call) {
                    try {
                        return String.valueOf(call.get());
                    } catch (RuntimeException thrown) {
                        return thrown.getClass().getSimpleName() + ": " + thrown.getMessage();
                    }
                }
            """;

    private static Execution execute(
            Map<String, byte[]> bytecode, List<JavaTaskSpec.Case> cases, JavaLimits limits) {
        if (isExhausted()) {
            throw new PracticeSubmissionException(SubmissionStatus.RUNTIME_ERROR, "practice.error.busy", null);
        }
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "practice-java");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Future<Execution> pending = executor.submit(callable(bytecode, cases, limits));
            return await(pending, limits);
        } finally {
            shutdown(executor);
        }
    }

    private static Callable<Execution> callable(
            Map<String, byte[]> bytecode, List<JavaTaskSpec.Case> cases, JavaLimits limits) {
        return () -> {
            SandboxOutput.begin(limits.maxOutputBytes());
            try {
                return invokeCases(new SandboxClassLoader(bytecode), cases);
            } finally {
                SandboxOutput.end();
            }
        };
    }

    private static Execution invokeCases(SandboxClassLoader loader, List<JavaTaskSpec.Case> cases)
            throws ClassNotFoundException {
        Class<?> harness = loader.loadClass(HARNESS_CLASS);
        List<List<Object>> rows = new ArrayList<>(cases.size());
        List<String> output = new ArrayList<>(cases.size());
        for (int i = 0; i < cases.size(); i++) {
            JavaTaskSpec.Case current = cases.get(i);
            int mark = SandboxOutput.mark();
            Object value = invoke(harness, i, current);
            output.add(SandboxOutput.since(mark));
            rows.add(List.of(current.label(), String.valueOf(ResultTable.normalise(value))));
        }
        return new Execution(
                new ResultTable(List.of("case", "result"), List.copyOf(rows), false), List.copyOf(output));
    }

    private static Object invoke(Class<?> harness, int index, JavaTaskSpec.Case current) {
        try {
            Method method = harness.getMethod("case" + index);
            return method.invoke(null);
        } catch (InvocationTargetException e) {
            throw runtimeFailure(current, e.getCause());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            // A class the policy refused surfaces here, as the loader's ClassNotFoundException
            // wrapped in a NoClassDefFoundError by whatever tried to resolve it.
            throw runtimeFailure(current, e);
        }
    }

    private static PracticeSubmissionException runtimeFailure(JavaTaskSpec.Case current, Throwable cause) {
        Throwable thrown = cause == null ? new IllegalStateException("no cause") : cause;
        String detail = current.label() + ": " + thrown.getClass().getName()
                + (thrown.getMessage() == null ? "" : ": " + thrown.getMessage());
        return new PracticeSubmissionException(
                SubmissionStatus.RUNTIME_ERROR, "practice.error.exception", detail);
    }

    private static Execution await(Future<Execution> pending, JavaLimits limits) {
        try {
            return pending.get(limits.runTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pending.cancel(true);
            throw new PracticeSubmissionException(
                    SubmissionStatus.TIMEOUT, "practice.error.timeout",
                    "limit=" + limits.runTimeoutSeconds() + "s");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof PracticeSubmissionException failure) {
                throw failure;
            }
            throw new PracticeSubmissionException(
                    SubmissionStatus.RUNTIME_ERROR, "practice.error.runtime",
                    cause == null ? e.toString() : cause.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PracticeSubmissionException(SubmissionStatus.RUNTIME_ERROR, "practice.error.busy", null);
        }
    }

    /**
     * Ends the attempt's thread, or writes it off. A submission that ignores its interrupt
     * cannot be stopped — {@code Thread.stop} was unsafe when it existed and is gone now — so
     * the only honest options are to leak the thread or to leak it and keep count.
     */
    private static void shutdown(ExecutorService executor) {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(INTERRUPT_GRACE_MILLIS, TimeUnit.MILLISECONDS)) {
                ABANDONED.incrementAndGet();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * @param results one row per case: its label and what it returned, normalised the same way
     *                SQL cells are, so that {@code 2} and {@code 2.0} compare equal
     * @param output  what each case printed, in the same order
     */
    record Execution(ResultTable results, List<String> output) {
    }

    /** Thrown when the source did not compile, carrying the messages that say why. */
    static final class CompilationFailure extends PracticeSubmissionException {

        private final transient List<CompileDiagnostic> diagnostics;

        CompilationFailure(List<CompileDiagnostic> diagnostics) {
            super(SubmissionStatus.COMPILE_ERROR, "practice.error.compile",
                    diagnostics.isEmpty() ? null : diagnostics.get(0).message());
            this.diagnostics = List.copyOf(diagnostics);
        }

        List<CompileDiagnostic> diagnostics() {
            return diagnostics;
        }
    }
}
