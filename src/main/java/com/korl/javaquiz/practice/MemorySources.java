package com.korl.javaquiz.practice;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compilation units and compiler output held in memory. Nothing a learner submits is ever
 * written to disk, so there is no temporary directory to clean up, collide over or leak.
 */
final class MemorySources {

    private MemorySources() {
    }

    /** One compilation unit, named after the class it is expected to declare. */
    static final class Source extends SimpleJavaFileObject {

        private final String code;

        Source(String className, String code) {
            super(URI.create("memory:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }

    /** One compiled class, kept as the bytes the class loader will define it from. */
    static final class Output extends SimpleJavaFileObject {

        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        Output(String className) {
            super(URI.create("memory:///" + className.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
        }

        @Override
        public OutputStream openOutputStream() {
            return bytes;
        }

        byte[] toByteArray() {
            return bytes.toByteArray();
        }
    }

    /**
     * A file manager that answers every write with an in-memory buffer, and leaves reads to
     * the standard one — which, with an empty class path, means the running JVM's own
     * {@code java.*} and nothing else.
     */
    static final class Manager extends ForwardingJavaFileManager<JavaFileManager> {

        private final Map<String, Output> compiled = new LinkedHashMap<>();

        Manager(JavaFileManager delegate) {
            super(delegate);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(
                Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
            Output output = new Output(className);
            compiled.put(className, output);
            return output;
        }

        /** The compiled classes by binary name, nested and anonymous ones included. */
        Map<String, byte[]> bytecode() {
            Map<String, byte[]> result = new LinkedHashMap<>();
            compiled.forEach((name, output) -> result.put(name, output.toByteArray()));
            return result;
        }
    }
}
