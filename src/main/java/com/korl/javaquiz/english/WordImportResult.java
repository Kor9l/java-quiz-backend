package com.korl.javaquiz.english;

import java.util.ArrayList;
import java.util.List;

/**
 * What a bulk import did. Unusable lines do not abort it: the good ones go in and the rest
 * come back named, so the learner fixes those instead of re-pasting everything.
 */
public class WordImportResult {

    private final List<LineError> errors = new ArrayList<>();
    private int imported;

    public void countImported() {
        imported++;
    }

    public void addError(int line, WordImportError code) {
        errors.add(new LineError(line, code));
    }

    public int getImported() {
        return imported;
    }

    public List<LineError> getErrors() {
        return List.copyOf(errors);
    }

    /**
     * One rejected line: which one, and what was wrong with it. The line number is 1-based, so
     * it matches what the learner is looking at.
     */
    public record LineError(int line, WordImportError code) {
    }
}
