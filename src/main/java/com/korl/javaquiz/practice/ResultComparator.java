package com.korl.javaquiz.practice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides whether a submitted query produced the same answer as the reference solution.
 *
 * <p>Column <em>labels</em> are deliberately ignored: the same result is the same result
 * whether the learner aliased a column {@code total} or left it as {@code sum(o.total)}.
 * Row order only matters when the task statement asked for a specific order.
 */
public final class ResultComparator {

    /** How many differing rows to hand back before the report stops being useful. */
    private static final int MAX_REPORTED_DIFFERENCES = 10;

    private ResultComparator() {
    }

    public static Comparison compare(ResultTable expected, ResultTable actual, boolean orderMatters) {
        if (expected.columnCount() != actual.columnCount()) {
            return Comparison.failed("practice.diff.columnCount", null, List.of(), List.of());
        }
        if (orderMatters) {
            return compareOrdered(expected, actual);
        }
        return compareAsBags(expected, actual);
    }

    private static Comparison compareOrdered(ResultTable expected, ResultTable actual) {
        int shared = Math.min(expected.rowCount(), actual.rowCount());
        for (int i = 0; i < shared; i++) {
            if (!expected.rows().get(i).equals(actual.rows().get(i))) {
                return Comparison.failed("practice.diff.rowMismatch", i, List.of(), List.of());
            }
        }
        if (expected.rowCount() != actual.rowCount()) {
            // The rows that do line up are identical, so the only problem is length. Point at
            // the first row that is missing or surplus rather than at row 0.
            boolean missing = actual.rowCount() < expected.rowCount();
            return Comparison.failed(
                    missing ? "practice.diff.missingRows" : "practice.diff.extraRows",
                    shared,
                    missing ? tail(expected.rows(), shared) : List.of(),
                    missing ? List.of() : tail(actual.rows(), shared));
        }
        return Comparison.identical();
    }

    private static Comparison compareAsBags(ResultTable expected, ResultTable actual) {
        Map<List<Object>, Integer> counts = new LinkedHashMap<>();
        for (List<Object> row : expected.rows()) {
            counts.merge(row, 1, Integer::sum);
        }
        List<List<Object>> unexpected = new ArrayList<>();
        for (List<Object> row : actual.rows()) {
            Integer remaining = counts.get(row);
            if (remaining == null || remaining == 0) {
                unexpected.add(row);
            } else if (remaining == 1) {
                counts.remove(row);
            } else {
                counts.put(row, remaining - 1);
            }
        }
        List<List<Object>> missing = new ArrayList<>();
        counts.forEach((row, count) -> {
            for (int i = 0; i < count; i++) {
                missing.add(row);
            }
        });
        if (missing.isEmpty() && unexpected.isEmpty()) {
            return Comparison.identical();
        }
        String key = missing.isEmpty() ? "practice.diff.extraRows"
                : unexpected.isEmpty() ? "practice.diff.missingRows"
                : "practice.diff.rowMismatch";
        return Comparison.failed(key, null, cap(missing), cap(unexpected));
    }

    private static List<List<Object>> tail(List<List<Object>> rows, int from) {
        return cap(new ArrayList<>(rows.subList(from, rows.size())));
    }

    private static List<List<Object>> cap(List<List<Object>> rows) {
        return List.copyOf(rows.size() <= MAX_REPORTED_DIFFERENCES
                ? rows
                : rows.subList(0, MAX_REPORTED_DIFFERENCES));
    }

    /**
     * @param reasonKey        message key naming how the results differ, null when they match
     * @param firstDifference  zero-based index of the first row that differs, when meaningful
     * @param missingRows      rows the reference produced that the submission did not
     * @param unexpectedRows   rows the submission produced that the reference did not
     */
    public record Comparison(
            boolean matched,
            String reasonKey,
            Integer firstDifference,
            List<List<Object>> missingRows,
            List<List<Object>> unexpectedRows) {

        static Comparison identical() {
            return new Comparison(true, null, null, List.of(), List.of());
        }

        static Comparison failed(
                String reasonKey,
                Integer firstDifference,
                List<List<Object>> missingRows,
                List<List<Object>> unexpectedRows) {
            return new Comparison(false, reasonKey, firstDifference, missingRows, unexpectedRows);
        }
    }
}
