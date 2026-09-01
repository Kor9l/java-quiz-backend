package com.korl.javaquiz.practice;

import java.util.Set;

/**
 * Cheap static policy applied before a submission reaches the engine.
 *
 * <p>This is the outer of two rings. The inner one is the sandbox itself, where the
 * submission runs as a database user that holds nothing but {@code SELECT} grants, so a
 * statement that slips past these checks still cannot change or read anything it shouldn't.
 * The point of the guard is to fail fast with a message a learner can act on.
 */
public final class SqlGuard {

    /**
     * Statement kinds rejected by name, so the learner is told they wrote the wrong <em>kind</em>
     * of statement instead of being handed a rights error from the engine.
     *
     * <p>Deliberately a denylist rather than an allowlist of {@code SELECT}/{@code WITH}: a word
     * that is on neither list is a typo, and the parser explains a typo far better than a
     * blanket "not a query" would. Letting it through costs nothing, because the sandbox user
     * has no rights beyond reading.
     */
    private static final Set<String> REFUSED_LEADING = Set.of(
            "INSERT", "UPDATE", "DELETE", "MERGE", "TRUNCATE",
            "CREATE", "DROP", "ALTER", "COMMENT",
            "GRANT", "REVOKE", "SET", "RESET",
            "CALL", "EXECUTE", "EXEC", "PREPARE", "DEALLOCATE",
            "COMMIT", "ROLLBACK", "SAVEPOINT",
            "SCRIPT", "RUNSCRIPT", "BACKUP", "CHECKPOINT", "SHUTDOWN", "ANALYZE", "HELP");

    private SqlGuard() {
    }

    public static void check(String sql, int maxLength) {
        if (sql == null || sql.isBlank()) {
            throw new PracticeSubmissionException(SubmissionStatus.POLICY_ERROR, "practice.error.empty", null);
        }
        if (sql.length() > maxLength) {
            throw new PracticeSubmissionException(
                    SubmissionStatus.POLICY_ERROR, "practice.error.tooLong", "limit=" + maxLength);
        }
        String masked = SqlText.mask(sql);
        if (SqlText.hasMultipleStatements(masked)) {
            throw new PracticeSubmissionException(SubmissionStatus.POLICY_ERROR, "practice.error.multipleStatements", null);
        }
        String keyword = SqlText.leadingKeyword(masked);
        if (keyword.isEmpty()) {
            throw new PracticeSubmissionException(SubmissionStatus.POLICY_ERROR, "practice.error.empty", null);
        }
        if (REFUSED_LEADING.contains(keyword)) {
            throw new PracticeSubmissionException(SubmissionStatus.POLICY_ERROR, "practice.error.notAQuery", keyword);
        }
    }
}
