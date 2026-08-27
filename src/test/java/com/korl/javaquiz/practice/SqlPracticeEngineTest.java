package com.korl.javaquiz.practice;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlPracticeEngineTest {

    private static final List<String> SETUP = List.of(
            "CREATE TABLE customers (id INT PRIMARY KEY, name VARCHAR(50), city VARCHAR(50))",
            "INSERT INTO customers VALUES (1,'Ann','Riga'),(2,'Bob','Vilnius'),(3,'Cid','Riga')",
            "CREATE TABLE orders (id INT PRIMARY KEY, customer_id INT, total NUMERIC(10,2))",
            "INSERT INTO orders VALUES (1,1,10.50),(2,1,5.00),(3,2,7.25)");

    private final SqlPracticeEngine engine = new SqlPracticeEngine(SandboxLimits.defaults());

    private static TaskSpec task(String id, String solution, boolean orderMatters) {
        return new TaskSpec(id, SETUP, solution, orderMatters);
    }

    @Test
    void acceptsADifferentlyWrittenQueryWithTheSameResult() {
        TaskSpec spec = task("join", "SELECT c.name FROM customers c JOIN orders o ON o.customer_id = c.id GROUP BY c.name", false);

        SubmissionOutcome outcome = engine.grade(spec,
                "SELECT name FROM customers WHERE id IN (SELECT customer_id FROM orders)");

        assertThat(outcome.status()).isEqualTo(SubmissionStatus.PASSED);
        assertThat(outcome.comparison().matched()).isTrue();
    }

    @Test
    void ignoresColumnLabels() {
        TaskSpec spec = task("labels", "SELECT name AS customer_name FROM customers WHERE city = 'Riga'", false);

        SubmissionOutcome outcome = engine.grade(spec, "SELECT c.name FROM customers c WHERE c.city = 'Riga'");

        assertThat(outcome.status()).isEqualTo(SubmissionStatus.PASSED);
    }

    @Test
    void treatsNumericTypesWithTheSameValueAsEqual() {
        // COUNT(*) is a BIGINT while SUM(1) is a NUMERIC; the answer is the same either way.
        TaskSpec spec = task("counts", "SELECT COUNT(*) FROM orders", false);

        assertThat(engine.grade(spec, "SELECT SUM(1) FROM orders").status()).isEqualTo(SubmissionStatus.PASSED);
        assertThat(engine.grade(spec, "SELECT CAST(3.000 AS NUMERIC(10,3)) FROM (VALUES(1)) v").status())
                .isEqualTo(SubmissionStatus.PASSED);
    }

    @Test
    void reportsAWrongResultRatherThanAnError() {
        TaskSpec spec = task("wrong", "SELECT name FROM customers WHERE city = 'Riga'", false);

        SubmissionOutcome outcome = engine.grade(spec, "SELECT name FROM customers");

        assertThat(outcome.status()).isEqualTo(SubmissionStatus.WRONG_RESULT);
        assertThat(outcome.comparison().unexpectedRows()).containsExactly(List.of("Bob"));
        assertThat(outcome.comparison().missingRows()).isEmpty();
        assertThat(outcome.expected()).isNotNull();
    }

    @Test
    void ignoresRowOrderUnlessTheTaskAsksForIt() {
        String solution = "SELECT name FROM customers ORDER BY name";
        String reversed = "SELECT name FROM customers ORDER BY name DESC";

        assertThat(engine.grade(task("unordered", solution, false), reversed).status())
                .isEqualTo(SubmissionStatus.PASSED);
        assertThat(engine.grade(task("ordered", solution, true), reversed).status())
                .isEqualTo(SubmissionStatus.WRONG_RESULT);
    }

    @Test
    void pointsAtTheFirstRowThatIsOutOfOrder() {
        SubmissionOutcome outcome = engine.grade(
                task("ordered-detail", "SELECT name FROM customers ORDER BY name", true),
                "SELECT name FROM customers ORDER BY name DESC");

        assertThat(outcome.comparison().firstDifference()).isZero();
        assertThat(outcome.comparison().reasonKey()).isEqualTo("practice.diff.rowMismatch");
    }

    @Test
    void reportsAColumnCountMismatchOnItsOwn() {
        SubmissionOutcome outcome = engine.grade(
                task("columns", "SELECT name FROM customers", false),
                "SELECT name, city FROM customers");

        assertThat(outcome.status()).isEqualTo(SubmissionStatus.WRONG_RESULT);
        assertThat(outcome.comparison().reasonKey()).isEqualTo("practice.diff.columnCount");
    }

    @Test
    void separatesSyntaxErrorsFromWrongAnswers() {
        TaskSpec spec = task("syntax", "SELECT name FROM customers", false);

        assertThatThrownBy(() -> engine.grade(spec, "SELEC name FROM customers"))
                .isInstanceOfSatisfying(SqlSubmissionException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(SubmissionStatus.SYNTAX_ERROR));
    }

    @Test
    void treatsAnUnknownColumnAsASyntaxError() {
        TaskSpec spec = task("unknown-column", "SELECT name FROM customers", false);

        assertThatThrownBy(() -> engine.grade(spec, "SELECT nam FROM customers"))
                .isInstanceOfSatisfying(SqlSubmissionException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(SubmissionStatus.SYNTAX_ERROR);
                    assertThat(e.getDetail()).contains("nam");
                });
    }

    @Test
    void checksSyntaxWithoutRunningTheStatement() {
        TaskSpec spec = task("check", "SELECT name FROM customers", false);

        assertThat(engine.checkSyntax(spec, "SELECT name FROM customers WHERE city = 'Riga'").status())
                .isEqualTo(SubmissionStatus.PASSED);
        assertThatThrownBy(() -> engine.checkSyntax(spec, "SELECT * FROM nope"))
                .isInstanceOfSatisfying(SqlSubmissionException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(SubmissionStatus.SYNTAX_ERROR));
    }

    @Test
    void rejectsAnythingThatIsNotASingleReadOnlyQuery() {
        TaskSpec spec = task("policy", "SELECT name FROM customers", false);

        for (String sql : List.of(
                "DELETE FROM customers",
                "DROP TABLE customers",
                "INSERT INTO customers VALUES (9, 'X', 'Y')",
                "CREATE ALIAS evil AS 'String x(String s) { return s; }'",
                "SELECT 1; DROP TABLE customers",
                "  ")) {
            assertThatThrownBy(() -> engine.grade(spec, sql))
                    .describedAs(sql)
                    .isInstanceOfSatisfying(SqlSubmissionException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(SubmissionStatus.POLICY_ERROR));
        }
    }

    @Test
    void blocksFileAccessEvenWhenItHidesInsideASelect() {
        TaskSpec spec = task("file-access", "SELECT name FROM customers", false);

        assertThatThrownBy(() -> engine.grade(spec, "SELECT FILE_READ('/etc/passwd')"))
                .isInstanceOfSatisfying(SqlSubmissionException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(SubmissionStatus.POLICY_ERROR));
    }

    @Test
    void doesNotMistakePunctuationInsideLiteralsForStructure() {
        TaskSpec spec = task("literals", "SELECT ';drop' FROM (VALUES(1)) v", false);

        SubmissionOutcome outcome = engine.grade(spec, "SELECT ';drop' -- a trailing comment\n");

        assertThat(outcome.status()).isEqualTo(SubmissionStatus.PASSED);
    }

    @Test
    void stopsAQueryThatRunsTooLong() {
        SqlPracticeEngine impatient = new SqlPracticeEngine(new SandboxLimits(1, 500, 4000, 50));
        TaskSpec spec = task("timeout", "SELECT COUNT(*) FROM customers", false);

        assertThatThrownBy(() -> impatient.grade(spec,
                "SELECT COUNT(*) FROM system_range(1, 200000000) a, system_range(1, 50) b"))
                .isInstanceOfSatisfying(SqlSubmissionException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(SubmissionStatus.TIMEOUT));
    }

    @Test
    void refusesASubmissionLongerThanTheLimit() {
        SqlPracticeEngine strict = new SqlPracticeEngine(new SandboxLimits(5, 500, 20, 50));

        assertThatThrownBy(() -> strict.grade(task("long", "SELECT 1", false),
                "SELECT name, city FROM customers WHERE city = 'Riga'"))
                .isInstanceOfSatisfying(SqlSubmissionException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(SubmissionStatus.POLICY_ERROR));
    }

    @Test
    void leavesNoDatabaseBehindAfterGrading() {
        TaskSpec spec = task("cleanup", "SELECT COUNT(*) FROM customers", false);

        engine.grade(spec, "SELECT COUNT(*) FROM customers");

        // A leaked in-memory database would keep answering; a fresh sandbox is the only way
        // the next attempt should ever see this data.
        assertThat(engine.grade(spec, "SELECT COUNT(*) FROM customers").passed()).isTrue();
    }
}
