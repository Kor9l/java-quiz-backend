package com.korl.javaquiz.practice;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the Java sandbox end to end: what it accepts, what it refuses, and what it does
 * with a submission that is merely wrong as opposed to hostile.
 */
class JavaPracticeEngineTest {

    private static final String SOLUTION = """
            public class Solution {
                public static int sum(int[] numbers) {
                    int total = 0;
                    for (int number : numbers) {
                        total += number;
                    }
                    return total;
                }
            }
            """;

    private final JavaPracticeEngine engine = new JavaPracticeEngine(JavaLimits.defaults());

    private static JavaTaskSpec task() {
        return new JavaTaskSpec("sum", "Solution", SOLUTION, List.of(
                new JavaTaskSpec.Case("sum({1, 2, 3})", "Solution.sum(new int[] {1, 2, 3})"),
                new JavaTaskSpec.Case("sum({})", "Solution.sum(new int[] {})"),
                new JavaTaskSpec.Case("sum({-4, 4})", "Solution.sum(new int[] {-4, 4})")));
    }

    @Test
    void theReferenceSolutionGradesItselfAsCorrect() {
        SubmissionOutcome outcome = engine.grade(task(), SOLUTION);

        assertThat(outcome.status()).isEqualTo(SubmissionStatus.PASSED);
        assertThat(outcome.passed()).isTrue();
        assertThat(outcome.result().rows()).hasSize(3);
    }

    /** The whole point of grading by result: a different implementation is still correct. */
    @Test
    void adifferentImplementationReachingTheSameValuesPasses() {
        String streams = """
                import java.util.stream.IntStream;

                public class Solution {
                    public static int sum(int[] numbers) {
                        return IntStream.of(numbers).sum();
                    }
                }
                """;

        assertThat(engine.grade(task(), streams).status()).isEqualTo(SubmissionStatus.PASSED);
    }

    @Test
    void aWrongAnswerIsReportedWithTheCaseThatDiffers() {
        String wrong = """
                public class Solution {
                    public static int sum(int[] numbers) {
                        return numbers.length;
                    }
                }
                """;

        SubmissionOutcome outcome = engine.grade(task(), wrong);

        assertThat(outcome.status()).isEqualTo(SubmissionStatus.WRONG_RESULT);
        assertThat(outcome.comparison().firstDifference()).isZero();
        assertThat(outcome.expected().rows()).hasSize(3);
    }

    @Test
    void sourceThatDoesNotCompileComesBackAsDiagnosticsRatherThanAStackTrace() {
        String broken = """
                public class Solution {
                    public static int sum(int[] numbers) {
                        return total;
                    }
                }
                """;

        SubmissionOutcome outcome = engine.grade(task(), broken);

        assertThat(outcome.status()).isEqualTo(SubmissionStatus.COMPILE_ERROR);
        assertThat(outcome.messageKey()).isEqualTo("practice.error.compile");
        assertThat(outcome.diagnostics()).isNotEmpty();
        assertThat(outcome.diagnostics().get(0).isError()).isTrue();
        assertThat(outcome.diagnostics().get(0).inSubmission()).isTrue();
        assertThat(outcome.diagnostics().get(0).line())
                .describedAs("the line the learner sees in their own editor").isEqualTo(3);
    }

    /**
     * A submission that compiles but does not expose what the cases call is a different failure
     * from one that does not compile, and gets a different key — the errors are in generated
     * code, at line numbers the learner has no way to look at.
     */
    @Test
    void aMissingMethodIsReportedAgainstTheShapeRatherThanTheSource() {
        String noSuchMethod = """
                public class Solution {
                    public static int total(int[] numbers) {
                        return 0;
                    }
                }
                """;

        SubmissionOutcome outcome = engine.grade(task(), noSuchMethod);

        assertThat(outcome.status()).isEqualTo(SubmissionStatus.COMPILE_ERROR);
        assertThat(outcome.messageKey()).isEqualTo("practice.error.signature");
        assertThat(outcome.diagnostics()).isNotEmpty();
        assertThat(outcome.diagnostics()).allSatisfy(diagnostic -> {
            assertThat(diagnostic.inSubmission()).isFalse();
            assertThat(diagnostic.line()).isZero();
        });
    }

    @Test
    void compilingWithoutRunningIsItsOwnAnswer() {
        SubmissionOutcome outcome = engine.checkCompilation(task(), SOLUTION);

        assertThat(outcome.status()).isEqualTo(SubmissionStatus.PASSED);
        assertThat(outcome.result()).isNull();
    }

    @Test
    void whatASubmissionPrintsIsCapturedPerCase() {
        String noisy = """
                public class Solution {
                    public static int sum(int[] numbers) {
                        System.out.println("summing " + numbers.length + " numbers");
                        int total = 0;
                        for (int number : numbers) {
                            total += number;
                        }
                        return total;
                    }
                }
                """;

        SubmissionOutcome outcome = engine.grade(task(), noisy);

        assertThat(outcome.status()).isEqualTo(SubmissionStatus.PASSED);
        assertThat(outcome.output()).hasSize(3);
        assertThat(outcome.output().get(0)).contains("summing 3 numbers");
        assertThat(outcome.output().get(1)).contains("summing 0 numbers");
    }

    @Test
    void anExceptionIsTheLearnersResultRatherThanTheServers() {
        String throwing = """
                public class Solution {
                    public static int sum(int[] numbers) {
                        return numbers[7];
                    }
                }
                """;

        assertThatThrownBy(() -> engine.grade(task(), throwing))
                .isInstanceOf(PracticeSubmissionException.class)
                .satisfies(thrown -> {
                    PracticeSubmissionException failure = (PracticeSubmissionException) thrown;
                    assertThat(failure.getStatus()).isEqualTo(SubmissionStatus.RUNTIME_ERROR);
                    assertThat(failure.getDetail()).contains("ArrayIndexOutOfBoundsException");
                });
    }

    @Test
    void aRunThatNeverEndsIsCutOff() {
        String spinning = """
                public class Solution {
                    public static int sum(int[] numbers) {
                        while (true) {
                            numbers = numbers.clone();
                        }
                    }
                }
                """;
        JavaPracticeEngine impatient = new JavaPracticeEngine(new JavaLimits(1, 20_000, 8_000));

        assertThatThrownBy(() -> impatient.grade(task(), spinning))
                .isInstanceOf(PracticeSubmissionException.class)
                .satisfies(thrown -> assertThat(((PracticeSubmissionException) thrown).getStatus())
                        .isEqualTo(SubmissionStatus.TIMEOUT));
    }
}
