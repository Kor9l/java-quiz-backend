package com.korl.javaquiz.practice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Guards the child JVM's class path, which is the one part of the Java track that no other test
 * can reach: it is assembled by name at runtime, so a renamed class or a newly introduced nested
 * one breaks the first submission after a deploy rather than the build.
 */
class ProcessSandboxTest {

    static List<String> runnerClasses() {
        return ProcessSandbox.runnerClasses();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("runnerClasses")
    void everyClassTheChildNeedsIsReadableFromThisJar(String simpleName) {
        assertThatCode(() -> {
            byte[] bytes = ProcessSandbox.runnerClassBytes(simpleName);
            assertThat(bytes).describedAs("not a class file").startsWith(
                    (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE);
        }).doesNotThrowAnyException();
    }

    /**
     * The list is exact on purpose. Anything the runner reaches that is not here fails in the
     * child with a {@code NoClassDefFoundError}, and anything here that the runner does not need
     * is application code sitting in a process that is meant to hold none.
     */
    @Test
    void theListIsTheWholeOfWhatTheChildIsGiven() {
        assertThat(ProcessSandbox.runnerClasses()).containsExactlyInAnyOrder(
                "PracticeRunner",
                "PracticeRunner$Capture",
                "SandboxClassLoader",
                "SandboxPolicy",
                "ResultTable");
    }
}
