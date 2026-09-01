package com.korl.javaquiz.practice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What the sandbox refuses, and — just as important — what it must not refuse.
 *
 * <p>Every refusal here is asserted through the engine rather than against
 * {@link SandboxPolicy} directly, because the policy is only as good as the two readings of it:
 * a submission that dodges the source check has to still be stopped by the bytecode one.
 */
class SandboxPolicyTest {

    private static final String IDENTITY = """
            public class Solution {
                public static int value() {
                    return 1;
                }
            }
            """;

    private final JavaPracticeEngine engine = new JavaPracticeEngine(JavaLimits.defaults());

    private static JavaTaskSpec task() {
        return new JavaTaskSpec("value", "Solution", IDENTITY,
                List.of(new JavaTaskSpec.Case("value()", "Solution.value()")));
    }

    private static String body(String statements) {
        return """
                public class Solution {
                    public static int value() {
                        %s
                        return 1;
                    }
                }
                """.formatted(statements);
    }

    /**
     * None of these name a forbidden class in an import, so the source-level guard cannot see
     * them. They are here to prove the bytecode reading of the policy stands on its own.
     */
    @ParameterizedTest(name = "{1}")
    @CsvSource(delimiter = '|', value = {
            "java.lang.Runtime.getRuntime().halt(0);            | Runtime",
            "System.exit(0);                                    | System.exit",
            "java.io.File.listRoots().toString();               | fully-qualified java.io",
            "java.nio.file.Files.exists(null);                  | java.nio.file",
            "java.net.InetAddress.getLoopbackAddress().toString(); | java.net",
            "Solution.class.getClassLoader().toString();        | ClassLoader through Class",
            "new Thread(() -> { }).start();                     | Thread",
            "System.getProperties().toString();                 | System.getProperties",
            "System.setOut(null);                               | System.setOut",
            "java.util.concurrent.Executors.newWorkStealingPool().shutdown(); | java.util.concurrent",
    })
    void submissionsReachingOutsideTheSandboxAreRefused(String statement, String name) {
        assertThatThrownBy(() -> engine.grade(task(), body(statement)))
                .describedAs(name)
                .isInstanceOf(PracticeSubmissionException.class)
                .satisfies(thrown -> assertThat(((PracticeSubmissionException) thrown).getStatus())
                        .isEqualTo(SubmissionStatus.POLICY_ERROR));
    }

    @Test
    void anImportOfAForbiddenPackageIsRefusedBeforeTheCompilerSeesIt() {
        String source = """
                import java.io.File;

                public class Solution {
                    public static int value() {
                        return new File(".").getName().length();
                    }
                }
                """;

        assertThatThrownBy(() -> engine.grade(task(), source))
                .isInstanceOf(PracticeSubmissionException.class)
                .satisfies(thrown -> {
                    PracticeSubmissionException failure = (PracticeSubmissionException) thrown;
                    assertThat(failure.getMessageKey()).isEqualTo("practice.error.forbiddenImport");
                    assertThat(failure.getDetail()).isEqualTo("java.io.File");
                });
    }

    @Test
    void reflectionIsRefusedBecauseItIsHowTheRestWouldBeReached() {
        String byName = body("""
                        try {
                            Class.forName("java.lang.Runtime");
                        } catch (ClassNotFoundException ignored) {
                        }
                """);

        assertThatThrownBy(() -> engine.grade(task(), byName))
                .isInstanceOf(PracticeSubmissionException.class)
                .satisfies(thrown -> {
                    PracticeSubmissionException failure = (PracticeSubmissionException) thrown;
                    assertThat(failure.getMessageKey()).isEqualTo("practice.error.forbiddenMember");
                    assertThat(failure.getDetail()).isEqualTo("java.lang.Class.forName");
                });
    }

    @Test
    void aPackageDeclarationIsRefusedBecauseItWouldHideTheClass() {
        assertThatThrownBy(() -> engine.grade(task(), "package sneaky;\n" + IDENTITY))
                .isInstanceOf(PracticeSubmissionException.class)
                .satisfies(thrown -> assertThat(((PracticeSubmissionException) thrown).getMessageKey())
                        .isEqualTo("practice.error.packageDeclaration"));
    }

    @Test
    void aSubmissionThatNeverDeclaresTheExpectedClassIsToldSo() {
        assertThatThrownBy(() -> engine.grade(task(), "public class Other { }"))
                .isInstanceOf(PracticeSubmissionException.class)
                .satisfies(thrown -> {
                    PracticeSubmissionException failure = (PracticeSubmissionException) thrown;
                    assertThat(failure.getMessageKey()).isEqualTo("practice.error.missingClass");
                    assertThat(failure.getDetail()).isEqualTo("Solution");
                });
    }

    @Test
    void aSubmissionLongerThanTheLimitIsRefusedWithoutCompiling() {
        JavaPracticeEngine strict = new JavaPracticeEngine(new JavaLimits(5, 40, 8_000));

        assertThatThrownBy(() -> strict.grade(task(), IDENTITY))
                .isInstanceOf(PracticeSubmissionException.class)
                .satisfies(thrown -> assertThat(((PracticeSubmissionException) thrown).getMessageKey())
                        .isEqualTo("practice.error.tooLong"));
    }

    /**
     * The other half of the policy. Lambdas, string concatenation, records, switch expressions
     * and text blocks all link through {@code java.lang.invoke} machinery a learner never
     * names, and refusing that machinery would refuse ordinary Java.
     */
    @Test
    void ordinaryJavaThatLinksThroughInvokeDynamicIsAllowed() {
        String modern = """
                import java.util.List;
                import java.util.stream.Collectors;

                public class Solution {

                    record Pair(String name, int size) { }

                    public static int value() {
                        List<Pair> pairs = List.of(new Pair("a", 1), new Pair("bb", 2));
                        String joined = pairs.stream()
                                .filter(pair -> pair.size() > 0)
                                .map(pair -> pair.name() + ":" + pair.size())
                                .collect(Collectors.joining(", "));
                        String described = switch (pairs.size()) {
                            case 2 -> "two: " + joined;
                            default -> "other";
                        };
                        System.out.println(described);
                        return pairs.stream().mapToInt(Pair::size).sum() - 2;
                    }
                }
                """;

        assertThatCode(() -> {
            SubmissionOutcome outcome = engine.grade(task(), modern);
            assertThat(outcome.status()).isEqualTo(SubmissionStatus.PASSED);
            assertThat(outcome.output().get(0)).contains("two: a:1, bb:2");
        }).doesNotThrowAnyException();
    }

    /** Statics do not carry over: each attempt gets its own loader, so each gets its own class. */
    @Test
    void oneAttemptCannotLeaveStateBehindForTheNext() {
        String counting = """
                public class Solution {
                    static int calls = 0;

                    public static int value() {
                        calls++;
                        return calls;
                    }
                }
                """;
        JavaTaskSpec twice = new JavaTaskSpec("value", "Solution", counting, List.of(
                new JavaTaskSpec.Case("first", "Solution.value()")));

        assertThat(engine.grade(twice, counting).status()).isEqualTo(SubmissionStatus.PASSED);
        assertThat(engine.grade(twice, counting).status()).isEqualTo(SubmissionStatus.PASSED);
    }
}
