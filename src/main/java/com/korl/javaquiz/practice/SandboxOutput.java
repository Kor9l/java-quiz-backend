package com.korl.javaquiz.practice;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Captures what a submission prints, per attempt, without stopping anything else from printing.
 *
 * <p>{@code System.out} is one field for the whole process, so capturing it by swapping it out
 * would mean either serialising every attempt or handing one learner another's output. What is
 * installed instead is a stream that routes by thread: a thread inside an attempt writes to
 * that attempt's buffer, and every other thread writes to the stream that was there before.
 * Installation happens once, on the first Java submission rather than at boot, which is after
 * the log manager has taken its own reference to the original — so application logging is not
 * routed anywhere and does not go through this at all.
 */
final class SandboxOutput {

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final ThreadLocal<Buffer> CURRENT = new ThreadLocal<>();

    private SandboxOutput() {
    }

    /** Starts capturing on this thread, up to {@code maxBytes} of output. */
    static void begin(int maxBytes) {
        install();
        CURRENT.set(new Buffer(maxBytes));
    }

    static void end() {
        CURRENT.remove();
    }

    /** How much has been captured so far, for slicing one case's output off the rest. */
    static int mark() {
        Buffer buffer = CURRENT.get();
        return buffer == null ? 0 : buffer.bytes.size();
    }

    /** The output written between two marks, decoded as UTF-8. */
    static String since(int mark) {
        Buffer buffer = CURRENT.get();
        if (buffer == null) {
            return "";
        }
        byte[] all = buffer.bytes.toByteArray();
        if (mark >= all.length) {
            return buffer.truncated ? "…" : "";
        }
        String text = new String(all, mark, all.length - mark, StandardCharsets.UTF_8);
        return buffer.truncated ? text + "…" : text;
    }

    private static void install() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        PrintStream original = System.out;
        System.setOut(new PrintStream(new Routing(original), true, StandardCharsets.UTF_8));
    }

    /** One attempt's captured bytes, stopping rather than growing once the cap is reached. */
    private static final class Buffer {

        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final int maxBytes;
        private boolean truncated;

        Buffer(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        void write(byte[] data, int offset, int length) {
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
    }

    private static final class Routing extends OutputStream {

        private final PrintStream fallback;

        Routing(PrintStream fallback) {
            this.fallback = fallback;
        }

        @Override
        public void write(int b) {
            write(new byte[] {(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] data, int offset, int length) {
            Buffer buffer = CURRENT.get();
            if (buffer == null) {
                fallback.write(data, offset, length);
            } else {
                buffer.write(data, offset, length);
            }
        }

        @Override
        public void flush() {
            if (CURRENT.get() == null) {
                fallback.flush();
            }
        }
    }
}
