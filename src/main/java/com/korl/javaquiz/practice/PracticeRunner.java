package com.korl.javaquiz.practice;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The {@code main} of the child JVM one submission is run in. Not called from the application:
 * {@link ProcessSandbox} starts it as a separate process and reads the file it leaves behind.
 *
 * <p>This is the half of the sandbox that had to move out of the server. Running a submission
 * in-process meant the only way to stop it was {@code interrupt}, which a deadlocked or spinning
 * thread never observes, and a thread a submission started outlived the attempt entirely — which
 * is why the policy refused {@link Thread} outright and why a topic about concurrency had no
 * practice track. In a child process the timeout is {@code destroyForcibly}, so a submission
 * that will not stop is killed rather than counted, and threads it started die with it.
 *
 * <p>Nothing here may reference the application: the child's class path holds this class, the
 * two sandbox classes it needs and {@link ResultTable}, and nothing else. Submitted classes are
 * <em>not</em> on that class path — they are read out of the work directory and defined by
 * {@link SandboxClassLoader}, whose parent is the platform loader, so a submission cannot reach
 * this class either.
 */
public final class PracticeRunner {

    /** Result-file format, so a stale file from another build is refused rather than misread. */
    static final int FORMAT_VERSION = 1;

    /** Every case returned a value. */
    static final byte OUTCOME_COMPLETED = 0;

    /** A case threw, and the ones after it were not run — the same as the in-process run did. */
    static final byte OUTCOME_THREW = 1;

    static final String CLASSES_DIR = "classes";
    static final String RESULT_FILE = "result.bin";

    /**
     * Class the case expressions are compiled into. Named not to collide with a submission, and
     * declared here rather than in {@link JavaSandbox} because this is the side that loads it:
     * the child's class path holds no application class, so a name it had to read from one
     * would not resolve.
     */
    static final String HARNESS_CLASS = "__PracticeHarness__";

    private PracticeRunner() {
    }

    /**
     * @param args the work directory, the output cap in bytes, and how many cases to call
     */
    public static void main(String[] args) {
        PrintStream diagnostics = System.err;
        int status = 0;
        try {
            Path work = Path.of(args[0]);
            run(work, Integer.parseInt(args[1]), Integer.parseInt(args[2]));
        } catch (Throwable failure) {
            // The parent reads this off the child's stderr and reports it as a runtime error.
            // Printed rather than written to the result file, because a failure here means the
            // result file is exactly what could not be produced.
            failure.printStackTrace(diagnostics);
            status = 1;
        }
        // halt rather than a return: a submission may have left non-daemon threads running, and
        // an ordinary exit would wait for every one of them. The results are already on disk.
        Runtime.getRuntime().halt(status);
    }

    private static void run(Path work, int maxOutputBytes, int caseCount) throws Exception {
        Map<String, byte[]> bytecode = readClasses(work.resolve(CLASSES_DIR));
        Capture capture = new Capture(maxOutputBytes);
        PrintStream captured = new PrintStream(capture, true, StandardCharsets.UTF_8);
        // Both streams, and for the whole run rather than per thread: in here the process has
        // one tenant, so anything printed by anything belongs to this attempt — including a
        // thread the submission started, whose output the in-process capture used to lose.
        System.setOut(captured);
        System.setErr(captured);

        Class<?> harness = new SandboxClassLoader(bytecode).loadClass(HARNESS_CLASS);
        List<String> values = new ArrayList<>(caseCount);
        List<String> output = new ArrayList<>(caseCount);
        String failure = null;
        for (int i = 0; i < caseCount && failure == null; i++) {
            int mark = capture.size();
            try {
                values.add(String.valueOf(ResultTable.normalise(invoke(harness, i))));
            } catch (Throwable thrown) {
                failure = describe(thrown);
            }
            output.add(capture.since(mark));
        }
        write(work.resolve(RESULT_FILE), values, output, failure);
    }

    private static Object invoke(Class<?> harness, int index)
            throws ReflectiveOperationException {
        Method method = harness.getMethod("case" + index);
        try {
            return method.invoke(null);
        } catch (InvocationTargetException e) {
            throw asThrown(e.getCause());
        }
    }

    /** Unwraps what the case actually threw, so the learner is not shown the reflection wrapper. */
    private static RuntimeException asThrown(Throwable cause) {
        if (cause instanceof RuntimeException runtime) {
            return runtime;
        }
        return new IllegalStateException(cause == null ? "no cause" : cause.toString(), cause);
    }

    /**
     * How a thrown value is named for the learner. The class name is fully qualified because
     * the point of a failing case is usually which exception it is.
     */
    private static String describe(Throwable thrown) {
        String message = thrown.getMessage();
        return thrown.getClass().getName() + (message == null ? "" : ": " + message);
    }

    /**
     * Reads the submission's compiled classes. The file name is the binary name — every class
     * here is in the default package, nested ones included ({@code Solution$1.class}).
     */
    private static Map<String, byte[]> readClasses(Path dir) throws IOException {
        Map<String, byte[]> bytecode = new HashMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (name.endsWith(".class")) {
                    bytecode.put(name.substring(0, name.length() - ".class".length()), Files.readAllBytes(file));
                }
            }
        }
        return bytecode;
    }

    /**
     * Leaves the results where the parent looks for them. A file rather than the child's
     * stdout, because stdout is the submission's: a learner printing something that looked
     * like the protocol would otherwise be able to confuse the grading of their own attempt.
     */
    private static void write(Path file, List<String> values, List<String> output, String failure)
            throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeInt(FORMAT_VERSION);
            out.writeByte(failure == null ? OUTCOME_COMPLETED : OUTCOME_THREW);
            if (failure != null) {
                // The index of the case that threw is the number of cases that returned.
                out.writeInt(values.size());
                writeText(out, failure);
            }
            out.writeInt(values.size());
            for (int i = 0; i < values.size(); i++) {
                writeText(out, values.get(i));
                writeText(out, output.get(i));
            }
        }
        Files.write(file, buffer.toByteArray());
    }

    /** Length-prefixed UTF-8, rather than {@code writeUTF}, which caps a string at 64 KB. */
    private static void writeText(DataOutputStream out, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    /**
     * What the submission printed, capped and shared. Synchronised because the submission may
     * now have several threads printing at once — which is the whole point of the change that
     * introduced this class.
     */
    static final class Capture extends OutputStream {

        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final int maxBytes;
        private boolean truncated;

        Capture(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public synchronized void write(int b) {
            write(new byte[] {(byte) b}, 0, 1);
        }

        @Override
        public synchronized void write(byte[] data, int offset, int length) {
            int room = maxBytes - bytes.size();
            if (room <= 0) {
                truncated = true;
                return;
            }
            if (length > room) {
                truncated = true;
                length = room;
            }
            bytes.write(data, offset, length);
        }

        synchronized int size() {
            return bytes.size();
        }

        /** What was printed since a mark, decoded as UTF-8. */
        synchronized String since(int mark) {
            byte[] all = bytes.toByteArray();
            if (mark >= all.length) {
                return truncated ? "…" : "";
            }
            String text = new String(all, mark, all.length - mark, StandardCharsets.UTF_8);
            return truncated ? text + "…" : text;
        }
    }
}
