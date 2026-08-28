package com.korl.javaquiz.practice;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A disposable database for grading one submission.
 *
 * <p>Each sandbox is a private in-memory H2 instance built from a dataset's DDL, thrown away
 * when the try-with-resources block ends. Nothing a learner writes can outlive their own
 * attempt or be seen by anyone else's.
 *
 * <p>Two connections are held open. The <em>admin</em> one builds the schema and runs the
 * bundled reference solution. The <em>restricted</em> one runs the submission and belongs to
 * a database user created with nothing but {@code SELECT} grants on the dataset tables — H2
 * refuses {@code INSERT}, {@code DROP}, {@code CREATE ALIAS}, {@code FILE_READ} and
 * {@code CSVREAD} for such a user, which is what keeps arbitrary submitted SQL harmless.
 */
public final class SqlSandbox implements AutoCloseable {

    private static final String SANDBOX_USER = "practice";

    private final String databaseName;
    private final SandboxLimits limits;
    private final Connection admin;
    private final Connection restricted;

    private SqlSandbox(String databaseName, SandboxLimits limits, Connection admin, Connection restricted) {
        this.databaseName = databaseName;
        this.limits = limits;
        this.admin = admin;
        this.restricted = restricted;
    }

    /**
     * Builds a sandbox and populates it. {@code setupStatements} is trusted content shipped
     * with the application, never anything a user supplied.
     */
    public static SqlSandbox create(List<String> setupStatements, SandboxLimits limits) {
        String name = "practice_" + UUID.randomUUID().toString().replace("-", "");
        // PostgreSQL mode with lower-cased identifiers, so that what learners type here behaves
        // the way it would against the Postgres this application itself runs on.
        String adminUrl = "jdbc:h2:mem:" + name
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=0;DEFAULT_LOCK_TIMEOUT=5000";
        Connection admin = null;
        Connection restricted = null;
        try {
            admin = DriverManager.getConnection(adminUrl, "sa", "");
            runSetup(admin, setupStatements);
            String password = UUID.randomUUID().toString();
            grantReadOnlyUser(admin, password);
            // Connect without settings: they are admin-only, and the database already exists.
            restricted = DriverManager.getConnection("jdbc:h2:mem:" + name, SANDBOX_USER, password);
            return new SqlSandbox(name, limits, admin, restricted);
        } catch (SQLException e) {
            closeQuietly(restricted);
            closeQuietly(admin);
            throw new IllegalStateException("Could not build the SQL sandbox for " + name, e);
        }
    }

    private static void runSetup(Connection admin, List<String> setupStatements) throws SQLException {
        try (Statement statement = admin.createStatement()) {
            for (String sql : setupStatements) {
                statement.execute(sql);
            }
        }
    }

    private static void grantReadOnlyUser(Connection admin, String password) throws SQLException {
        List<String> tables = new ArrayList<>();
        // Only the dataset's own tables. PostgreSQL mode also exposes an emulated pg_catalog,
        // which is readable without a grant and cannot be granted on anyway.
        try (Statement statement = admin.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT table_name FROM information_schema.tables "
                             + "WHERE UPPER(table_schema) = 'PUBLIC' ORDER BY table_name")) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        if (tables.isEmpty()) {
            throw new IllegalStateException("Dataset setup produced no tables to grant");
        }
        try (Statement statement = admin.createStatement()) {
            statement.execute("CREATE USER " + quote(SANDBOX_USER) + " PASSWORD " + literal(password));
            StringBuilder grant = new StringBuilder("GRANT SELECT ON ");
            for (int i = 0; i < tables.size(); i++) {
                grant.append(i == 0 ? "" : ", ").append(quote(tables.get(i)));
            }
            grant.append(" TO ").append(quote(SANDBOX_USER));
            statement.execute(grant.toString());
        }
    }

    /** Reads back the dataset's tables and columns, for showing learners what they can query. */
    public SchemaInfo describeSchema() {
        List<SchemaInfo.Table> tables = new ArrayList<>();
        String currentTable = null;
        List<SchemaInfo.Column> columns = new ArrayList<>();
        try (Statement statement = admin.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT table_name, column_name, data_type, is_nullable FROM information_schema.columns "
                             + "WHERE UPPER(table_schema) = 'PUBLIC' ORDER BY table_name, ordinal_position")) {
            while (rs.next()) {
                String table = rs.getString(1);
                if (!table.equals(currentTable)) {
                    if (currentTable != null) {
                        tables.add(new SchemaInfo.Table(currentTable, List.copyOf(columns)));
                    }
                    currentTable = table;
                    columns.clear();
                }
                columns.add(new SchemaInfo.Column(
                        rs.getString(2), rs.getString(3), "YES".equalsIgnoreCase(rs.getString(4))));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read the sandbox schema", e);
        }
        if (currentTable != null) {
            tables.add(new SchemaInfo.Table(currentTable, List.copyOf(columns)));
        }
        return new SchemaInfo(List.copyOf(tables));
    }

    /** Runs bundled, trusted SQL with full rights — used for the reference solution. */
    public ResultTable runReference(String sql) {
        try (Statement statement = admin.createStatement()) {
            statement.setQueryTimeout(limits.queryTimeoutSeconds());
            statement.setMaxRows(limits.maxRows() + 1);
            try (ResultSet rs = statement.executeQuery(sql)) {
                return ResultTable.capture(rs, limits.maxRows());
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Reference solution failed: " + cleanMessage(e), e);
        }
    }

    /**
     * Parses the submission without running it. H2 resolves tables and columns at prepare
     * time, so this catches typos and unknown identifiers as well as pure syntax errors.
     */
    public void checkSyntax(String sql) {
        try (PreparedStatement ignored = restricted.prepareStatement(sql)) {
            // Preparing is the whole check.
        } catch (SQLException e) {
            throw toSubmissionException(e, SubmissionStatus.SYNTAX_ERROR);
        }
    }

    /** Runs a submission as the restricted user, under the configured time and row limits. */
    public ResultTable runSubmission(String sql) {
        try (Statement statement = restricted.createStatement()) {
            statement.setQueryTimeout(limits.queryTimeoutSeconds());
            statement.setMaxRows(limits.maxRows() + 1);
            try (ResultSet rs = statement.executeQuery(sql)) {
                return ResultTable.capture(rs, limits.maxRows());
            }
        } catch (SQLException e) {
            throw toSubmissionException(e, SubmissionStatus.RUNTIME_ERROR);
        }
    }

    private SqlSubmissionException toSubmissionException(SQLException e, SubmissionStatus fallback) {
        SubmissionStatus status = classify(e, fallback);
        String key = switch (status) {
            case TIMEOUT -> "practice.error.timeout";
            case SYNTAX_ERROR -> "practice.error.syntax";
            case POLICY_ERROR -> "practice.error.notAQuery";
            default -> "practice.error.runtime";
        };
        return new SqlSubmissionException(status, key, cleanMessage(e));
    }

    private static SubmissionStatus classify(SQLException e, SubmissionStatus fallback) {
        String state = e.getSQLState() == null ? "" : e.getSQLState();
        if (state.startsWith("57014")) {
            return SubmissionStatus.TIMEOUT;
        }
        // 42xxx covers syntax errors and unresolved tables/columns; 21xxx covers a column
        // count that does not line up. Both mean "this statement does not make sense here".
        if (state.startsWith("42") || state.startsWith("21")) {
            return SubmissionStatus.SYNTAX_ERROR;
        }
        return switch (e.getErrorCode()) {
            // Aggregate/function misuse: still the learner's statement being wrong, not a crash.
            case 90016, 90022, 90059 -> SubmissionStatus.SYNTAX_ERROR;
            // The restricted user lacking rights means the statement tried to do more than read.
            case 90040, 90096 -> SubmissionStatus.POLICY_ERROR;
            default -> fallback;
        };
    }

    /** Trims H2's echoed statement and its {@code [code-version]} suffix off the message. */
    static String cleanMessage(SQLException e) {
        String message = e.getMessage();
        if (message == null) {
            return "SQL error " + e.getErrorCode();
        }
        int echo = message.indexOf("; SQL statement:");
        if (echo > 0) {
            message = message.substring(0, echo);
        }
        return message.replaceAll("\\s*\\[\\d+-\\d+]\\s*$", "").trim();
    }

    private static String quote(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    private static String literal(String text) {
        return '\'' + text.replace("'", "''") + '\'';
    }

    @Override
    public void close() {
        closeQuietly(restricted);
        try (Statement statement = admin.createStatement()) {
            // Belt and braces: DB_CLOSE_DELAY=0 already drops the database with the last
            // connection, but an explicit shutdown makes the reclaim immediate.
            statement.execute("SHUTDOWN IMMEDIATELY");
        } catch (SQLException ignored) {
            // Shutting down always ends by severing this very connection.
        }
        closeQuietly(admin);
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Nothing useful to do while tearing a throwaway database down.
        }
    }

    @Override
    public String toString() {
        return "SqlSandbox[" + databaseName + "]";
    }
}
