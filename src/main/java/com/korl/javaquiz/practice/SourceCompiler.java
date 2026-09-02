package com.korl.javaquiz.practice;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Compiles submitted source in memory and hands back either the bytecode or the diagnostics.
 *
 * <p>The compiler is the JDK's own, reached through {@link ToolProvider}. That makes the
 * runtime image a hard requirement rather than a detail: {@code getSystemJavaCompiler()}
 * returns null on a JRE, so the Dockerfile runs on {@code eclipse-temurin:17-jdk-alpine} and
 * asserts at build time that {@code jdk.compiler} is there.
 *
 * <p>It was ECJ, bundled as a dependency, precisely so that a JRE would do — and that was
 * wrong twice over. ECJ's {@code javax.tools} bridge reads compilation units from the file
 * system and refuses a {@link JavaFileObject} with no file behind it, so it could never have
 * compiled anything here. And it was never reached in testing: on a JDK,
 * {@code ServiceLoader} enumerates the {@code jdk.compiler} module's provider before any on
 * the class path, so every test ran on javac while only production would have run on ECJ.
 * Asking for one compiler by name is what keeps the tested path and the deployed path the
 * same one.
 *
 * <p>The class path is emptied before compiling, so submitted code sees the JVM's own
 * {@code java.*} and the units handed to it, and does not see this application at all.
 */
final class SourceCompiler {

    /** Beyond this the list stops being a list of things to fix and becomes a wall of text. */
    private static final int MAX_DIAGNOSTICS = 25;

    private static final List<String> OPTIONS = List.of(
            "-source", "17",
            "-target", "17",
            "-encoding", "UTF-8",
            // Nothing here declares an annotation processor, and discovery would be one more
            // way for the class path to matter.
            "-proc:none",
            // Warnings are dropped on purpose: bootclasspath and deprecation notes are about
            // the way this compiles, not about what a learner got wrong.
            "-nowarn");

    /** Errors before warnings, then in source order, so the top of the list is where to start. */
    private static final Comparator<Diagnostic<? extends JavaFileObject>> WORST_FIRST =
            Comparator.<Diagnostic<? extends JavaFileObject>, Boolean>comparing(
                            diagnostic -> diagnostic.getKind() != Diagnostic.Kind.ERROR)
                    .thenComparingLong(Diagnostic::getLineNumber)
                    .thenComparingLong(Diagnostic::getColumnNumber);

    private SourceCompiler() {
    }

    /**
     * The compiler this JVM offers, or a failure naming what is missing. Called on the first
     * submission rather than at boot, so a misconfigured runtime shows up as a broken practice
     * track instead of a service that will not start.
     */
    static JavaCompiler compiler() {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException(
                    "No system Java compiler: this JVM has no jdk.compiler module. The Java "
                            + "practice track needs a JDK at runtime, not a JRE.");
        }
        return compiler;
    }

    /**
     * @param sources compilation units, the learner's own first
     * @return the outcome, whose {@link Result#succeeded()} says whether the bytecode is usable
     */
    static Result compile(List<MemorySources.Source> sources) {
        JavaCompiler compiler = compiler();
        DiagnosticCollector<JavaFileObject> collected = new DiagnosticCollector<>();
        StandardJavaFileManager standard = compiler.getStandardFileManager(collected, null, null);
        try (MemorySources.Manager files = new MemorySources.Manager(standard)) {
            // Emptied through the file manager rather than with an option, because an empty
            // -classpath argument means the working directory rather than nothing.
            standard.setLocation(StandardLocation.CLASS_PATH, List.of());
            boolean succeeded = compiler.getTask(null, files, collected, OPTIONS, null, sources).call();
            return new Result(
                    succeeded,
                    succeeded ? files.bytecode() : Map.of(),
                    // The first unit is the submission, and the rest is generated: which one a
                    // diagnostic came from decides whether it has a line the learner can find.
                    diagnostics(collected, sources.get(0).getName()));
        } catch (IOException e) {
            throw new IllegalStateException("Could not compile in memory", e);
        }
    }

    private static List<CompileDiagnostic> diagnostics(
            DiagnosticCollector<JavaFileObject> collected, String submissionFile) {
        List<Diagnostic<? extends JavaFileObject>> raw = new ArrayList<>(collected.getDiagnostics());
        raw.removeIf(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.NOTE);
        raw.sort(WORST_FIRST);
        List<CompileDiagnostic> reported = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> diagnostic : raw.subList(0, Math.min(raw.size(), MAX_DIAGNOSTICS))) {
            reported.add(CompileDiagnostic.from(diagnostic, submissionFile));
        }
        return List.copyOf(reported);
    }

    /**
     * @param succeeded   whether the compiler produced usable bytecode
     * @param bytecode    compiled classes by binary name, empty when it did not
     * @param diagnostics what the compiler had to say, errors first
     */
    record Result(boolean succeeded, Map<String, byte[]> bytecode, List<CompileDiagnostic> diagnostics) {
    }
}
