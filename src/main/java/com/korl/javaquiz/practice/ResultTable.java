package com.korl.javaquiz.practice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * A materialised result set: column labels plus rows of already-normalised cells.
 *
 * <p>Cells are normalised on capture so that two statements written differently but meaning
 * the same thing compare equal — {@code COUNT(*)} returning a {@code BIGINT} and
 * {@code SUM(1)} returning a {@code NUMERIC} both become the same decimal.
 */
public record ResultTable(List<String> columns, List<List<Object>> rows, boolean truncated) {

    /** Decimals are compared at this scale; beyond it, results differ only by float noise. */
    private static final int COMPARISON_SCALE = 6;

    public static ResultTable capture(ResultSet rs, int maxRows) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        List<String> columns = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            columns.add(meta.getColumnLabel(i));
        }
        List<List<Object>> rows = new ArrayList<>();
        boolean truncated = false;
        while (rs.next()) {
            if (rows.size() >= maxRows) {
                truncated = true;
                break;
            }
            List<Object> row = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                row.add(normalise(rs.getObject(i)));
            }
            rows.add(row);
        }
        return new ResultTable(List.copyOf(columns), List.copyOf(rows), truncated);
    }

    public int rowCount() {
        return rows.size();
    }

    public int columnCount() {
        return columns.size();
    }

    /** The first {@code limit} rows, for showing a preview without shipping a huge payload. */
    public ResultTable preview(int limit) {
        if (rows.size() <= limit) {
            return this;
        }
        return new ResultTable(columns, List.copyOf(rows.subList(0, limit)), true);
    }

    /**
     * Collapses a JDBC value to a form that only depends on what the value <em>is</em>,
     * not on which SQL type the engine happened to pick for it.
     */
    static Object normalise(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return decimalKey(number);
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof byte[] bytes) {
            return HexFormat.of().formatHex(bytes);
        }
        if (value instanceof String text) {
            // CHAR columns come back space-padded to their declared width; that padding is an
            // artefact of the storage type, not something the learner got wrong.
            return stripTrailing(text);
        }
        return value.toString();
    }

    private static String decimalKey(Number number) {
        BigDecimal decimal;
        if (number instanceof BigDecimal big) {
            decimal = big;
        } else if (number instanceof Double || number instanceof Float) {
            double d = number.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return String.valueOf(d);
            }
            decimal = BigDecimal.valueOf(d);
        } else {
            decimal = new BigDecimal(number.toString());
        }
        BigDecimal rounded = decimal.setScale(COMPARISON_SCALE, RoundingMode.HALF_UP);
        return rounded.signum() == 0 ? "0" : rounded.stripTrailingZeros().toPlainString();
    }

    private static String stripTrailing(String text) {
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == ' ') {
            end--;
        }
        return text.substring(0, end);
    }
}
