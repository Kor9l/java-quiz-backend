package com.korl.javaquiz.practice;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Runs one submission's compiled classes in a child JVM and reads back what each case returned.
 *
 * <p>The timeout here is a kill, not a request. That is the whole reason this exists: in-process
 * the only lever was {@code Thread.interrupt}, which is cooperative — a submission spinning in a
 * tight loop or parked on a deadlock never observes it, {@code Thread.stop} is gone, and so the
 * old sandbox could do nothing but abandon such a thread and count it, shutting the track down
 * once four had piled up. A concurrency exercise is precisely the kind that deadlocks, so the
 * policy had to refuse threads outright and the topic had no practice track at all.
 *
 * <p>{@link Process#destroyForcibly()} ends that: an attempt that overruns is killed with every
 * thread it started, nothing is leaked into the server, and the policy can afford to let a
 * submission use {@code java.util.concurrent}.
 *
 * <p>What is still bounded only loosely is memory: {@code -Xmx} caps the heap and {@code -Xss}
 * keeps a thread's stack small, but a submission that starts thousands of threads can still cost
 * the host more than it should until the timeout fires. The concurrency limit in
 * {@link JavaPracticeEngine} is what keeps that to one or two processes at a time.
 */
final class ProcessSandbox {

    /**
     * The child's class path. Enumerated rather than discovered, because the child must hold
     * exactly these: nothing of the application, and nothing that pulls Quarkus in behind it.
     * {@code ProcessSandboxTest} asserts every name here resolves, so a rename or a new nested
     * class fails the build rather than the first submission after a deploy.
     */
    private static final List<String> RUNNER_CLASSES = List.of(
            "PracticeRunner",
            "PracticeRunner$Capture",
            "SandboxClassLoader",
            "SandboxPolicy",
            "ResultTable");

    /** Interpreted flat and with a small stack: a run is short, and most of it is start-up. */
    private static final List<String> JVM_OPTIONS = List.of(
            "-XX:TieredStopAtLevel=1",
            "-XX:+UseSerialGC",
            "-Xss512k",
            "-Dfile.encoding=UTF-8");

    /** Grace after a kill, only to reap the process — it is already dead when this is waited on. */
    private static final long KILL_GRACE_SECONDS = 5;

    /** Of the child's stderr, what is worth putting in front of a learner or into a log. */
    private static final int MAX_STDERR_CHARS = 2_000;

    private ProcessSandbox() {
    }

    /**
     * @param bytecode the submission's compiled classes, already checked by {@link ClassFileGuard}
     * @param cases    the calls to make, in the order they are reported
     * @throws PracticeSubmissionException when the run overruns, throws, or cannot be started
     */
    static JavaSandbox.Execution run(
            Map<String, byte[]> bytecode, List<JavaTaskSpec.Case> cases, JavaLimits limits) {
        Path work = createWorkDirectory();
        try {
            writeClasses(work.resolve(PracticeRunner.CLASSES_DIR), bytecode);
            Path stderr = work.resolve("stderr.log");
            Process process = start(work, stderr, cases.size(), limits);
            awaitExit(process, limits);
            return read(work.resolve(PracticeRunner.RESULT_FILE), cases, process.exitValue(), stderr);
        } finally {
            deleteRecursively(work);
        }
    }

    private static Process start(Path work, Path stderr, int caseCount, JavaLimits limits) {
        List<String> command = new ArrayList<>();
        command.add(javaBinary().toString());
        command.add("-Xmx" + limits.heapMegabytes() + "m");
        command.addAll(JVM_OPTIONS);
        command.add("-cp");
        command.add(RunnerClasspath.directory().toString());
        command.add(PracticeRunner.class.getName());
        command.add(work.toString());
        command.add(String.valueOf(limits.maxOutputBytes()));
        command.add(String.valueOf(caseCount));
        try {
            return new ProcessBuilder(command)
                    .directory(work.toFile())
                    // Whatever the submission prints is captured inside the child and comes back
                    // in the result file, so the child's own stdout should stay empty.
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.to(stderr.toFile()))
                    .start();
        } catch (IOException e) {
            throw new PracticeSubmissionException(
                    SubmissionStatus.RUNTIME_ERROR, "practice.error.runtime",
                    "could not start the sandbox process: " + e.getMessage());
        }
    }

    private static void awaitExit(Process process, JavaLimits limits) {
        boolean exited;
        try {
            exited = process.waitFor(limits.runTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new PracticeSubmissionException(SubmissionStatus.RUNTIME_ERROR, "practice.error.busy", null);
        }
        if (exited) {
            return;
        }
        kill(process);
        throw new PracticeSubmissionException(
                SubmissionStatus.TIMEOUT, "practice.error.timeout",
                "limit=" + limits.runTimeoutSeconds() + "s");
    }

    private static void kill(Process process) {
        process.destroyForcibly();
        try {
            // Only so the process is reaped rather than left a zombie; it is already gone.
            process.waitFor(KILL_GRACE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Turns the child's result file into an execution, or into the failure that explains why
     * there is none.
     */
    private static JavaSandbox.Execution read(
            Path result, List<JavaTaskSpec.Case> cases, int exitCode, Path stderr) {
        if (!Files.exists(result)) {
            throw new PracticeSubmissionException(
                    SubmissionStatus.RUNTIME_ERROR, "practice.error.runtime",
                    "the sandbox process exited with " + exitCode + tail(stderr));
        }
        try (DataInputStream in = new DataInputStream(Files.newInputStream(result))) {
            int version = in.readInt();
            if (version != PracticeRunner.FORMAT_VERSION) {
                throw new PracticeSubmissionException(
                        SubmissionStatus.RUNTIME_ERROR, "practice.error.runtime",
                        "sandbox result format " + version);
            }
            byte outcome = in.readByte();
            if (outcome == PracticeRunner.OUTCOME_THREW) {
                int index = in.readInt();
                String detail = readText(in);
                throw thrownByCase(cases, index, detail);
            }
            return execution(in, cases);
        } catch (IOException e) {
            throw new UncheckedIOException("Unreadable sandbox result", e);
        }
    }

    private static JavaSandbox.Execution execution(DataInputStream in, List<JavaTaskSpec.Case> cases)
            throws IOException {
        int count = in.readInt();
        List<List<Object>> rows = new ArrayList<>(count);
        List<String> output = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String value = readText(in);
            output.add(readText(in));
            rows.add(List.of(cases.get(i).label(), value));
        }
        return new JavaSandbox.Execution(
                new ResultTable(List.of("case", "result"), List.copyOf(rows), false), List.copyOf(output));
    }

    private static PracticeSubmissionException thrownByCase(
            List<JavaTaskSpec.Case> cases, int index, String detail) {
        String label = index >= 0 && index < cases.size() ? cases.get(index).label() : "case " + index;
        return new PracticeSubmissionException(
                SubmissionStatus.RUNTIME_ERROR, "practice.error.exception", label + ": " + detail);
    }

    private static String readText(DataInputStream in) throws IOException {
        byte[] bytes = new byte[in.readInt()];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** The last of the child's own stderr, when it died with something to say. */
    private static String tail(Path stderr) {
        try {
            if (!Files.exists(stderr)) {
                return "";
            }
            String text = Files.readString(stderr, StandardCharsets.UTF_8).strip();
            if (text.isEmpty()) {
                return "";
            }
            return ": " + (text.length() <= MAX_STDERR_CHARS ? text : text.substring(0, MAX_STDERR_CHARS) + "…");
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * The JVM running this one. Taken from {@code java.home} rather than from the {@code PATH},
     * so the child is the same runtime as the server — which is also what makes the JDK
     * requirement a single one rather than two.
     */
    private static Path javaBinary() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        return Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java");
    }

    private static Path createWorkDirectory() {
        try {
            return Files.createTempDirectory("practice-java-");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create a sandbox work directory", e);
        }
    }

    private static void writeClasses(Path dir, Map<String, byte[]> bytecode) {
        try {
            Files.createDirectories(dir);
            for (Map.Entry<String, byte[]> entry : bytecode.entrySet()) {
                Files.write(dir.resolve(entry.getKey() + ".class"), entry.getValue());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not stage the submission for the sandbox", e);
        }
    }

    private static void deleteRecursively(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            // A leftover temp directory is untidy, not broken, and saying so is more useful
            // than failing an attempt that has already produced its answer.
            System.getLogger(ProcessSandbox.class.getName())
                    .log(System.Logger.Level.WARNING, "Could not clean up " + root, e);
        }
    }

    /**
     * The directory the child's class path points at, written once per process.
     *
     * <p>The classes are copied out of this application's own jar as resources rather than by
     * pointing the child at the server's class path: under a Quarkus fast-jar that class path is
     * a launcher and a tree of jars, and handing all of it to a process meant to hold nothing
     * would defeat the point.
     */
    private static final class RunnerClasspath {

        private static final Path DIRECTORY = materialise();

        private RunnerClasspath() {
        }

        static Path directory() {
            return DIRECTORY;
        }

        private static Path materialise() {
            try {
                Path dir = Files.createTempDirectory("practice-runner-");
                dir.toFile().deleteOnExit();
                Path packageDir = dir.resolve(ProcessSandbox.class.getPackageName().replace('.', '/'));
                Files.createDirectories(packageDir);
                for (String simpleName : RUNNER_CLASSES) {
                    Path file = packageDir.resolve(simpleName + ".class");
                    Files.write(file, classBytes(simpleName));
                    file.toFile().deleteOnExit();
                }
                return dir;
            } catch (IOException e) {
                throw new UncheckedIOException("Could not lay out the sandbox class path", e);
            }
        }

        static byte[] classBytes(String simpleName) throws IOException {
            String resource = "/" + ProcessSandbox.class.getPackageName().replace('.', '/')
                    + "/" + simpleName + ".class";
            try (InputStream in = ProcessSandbox.class.getResourceAsStream(resource)) {
                if (in == null) {
                    throw new IOException("Missing sandbox class resource: " + resource);
                }
                return in.readAllBytes();
            }
        }
    }

    /** For the test that asserts the child's class path is complete and loadable. */
    static List<String> runnerClasses() {
        return RUNNER_CLASSES;
    }

    static byte[] runnerClassBytes(String simpleName) throws IOException {
        return RunnerClasspath.classBytes(simpleName);
    }
}
