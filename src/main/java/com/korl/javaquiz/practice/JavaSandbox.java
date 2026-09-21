package com.korl.javaquiz.practice;

import java.util.List;

/**
 * Compiles one submission and has it run against the task's cases.
 *
 * <p>Two stages in two places. Compilation happens here, in the server process, because that is
 * where the diagnostics a learner needs are produced and where {@link ClassFileGuard} can read
 * the bytecode before anything loads it. Execution happens in a child JVM — see
 * {@link ProcessSandbox} — because that is the only place a submission can be stopped for
 * certain.
 *
 * <p>It used to run here too, on a throwaway thread with a throwaway class loader, and the
 * comment in this file said plainly what that cost: a timeout was an {@code interrupt}, a
 * submission that ignored it could only be abandoned, and four abandoned threads shut the whole
 * Java track down. The class loader still does its job, now on the other side of the process
 * boundary; what the boundary adds is that overrunning is a kill.
 */
final class JavaSandbox {

    /** Class the case expressions are compiled into, loaded by the runner on the other side. */
    static final String HARNESS_CLASS = PracticeRunner.HARNESS_CLASS;

    private JavaSandbox() {
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
        return ProcessSandbox.run(compiled.bytecode(), task.cases(), limits);
    }

    /**
     * Imports a case expression is written against, so that a case reads
     * {@code Solution.max(List.of(1, 2))} rather than spelling out {@code java.util.List}.
     * Wildcards from the sandbox's own allowlist; nothing here widens what a submission may do,
     * since the submission is a separate compilation unit with its own imports.
     */
    private static final String HARNESS_IMPORTS = """
            import java.util.*;
            import java.util.concurrent.*;
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
