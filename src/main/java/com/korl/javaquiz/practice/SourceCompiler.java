package com.korl.javaquiz.practice;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Compiles submitted source in memory and hands back either the bytecode or the diagnostics.
 *
 * <p>The compiler is ECJ, loaded through the {@code javax.tools} service interface, rather than
 * the JDK's own. Two reasons, in order of how much they matter here. The runtime image is
 * {@code eclipse-temurin:17-jre}, and on a JRE {@code ToolProvider.getSystemJavaCompiler()}
 * returns null — there is no {@code jdk.compiler} module to find, and switching to a JDK image
 * is the larger change. And a bundled compiler means the error message a learner reads is the
 * one the build tested against, on every machine this runs on.
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
            // Warnings are dropped on purpose: ECJ's defaults flag raw types, boxing and
            // missing serialVersionUID, none of which is what a learner got wrong.
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
     * @param sources compilation units, the learner's own first
     * @return the outcome, whose {@link Result#succeeded()} says whether the bytecode is usable
     */
    static Result compile(List<MemorySources.Source> sources) {
        JavaCompiler compiler = ServiceLoader.load(JavaCompiler.class, SourceCompiler.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No javax.tools.JavaCompiler on the class path"));
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
