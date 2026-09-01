package com.korl.javaquiz.english;

import java.util.ArrayList;
import java.util.List;

/**
 * What a bulk import did. Unusable lines do not abort it: the good ones go in and the rest
 * come back named, so the learner fixes those instead of re-pasting everything.
 */
public class WordImportResult {

    private final List<String> errors = new ArrayList<>();
    private int imported;

    public void countImported() {
        imported++;
    }

    public void addError(int lineNumber, String message) {
        errors.add("Line " + lineNumber + ": " + message);
    }

    public int getImported() {
        return imported;
    }

    public List<String> getErrors() {
        return List.copyOf(errors);
    }
}
