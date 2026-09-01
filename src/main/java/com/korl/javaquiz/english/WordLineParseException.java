package com.korl.javaquiz.english;

/**
 * One unusable line. Thrown per line so an import reports them all instead of stopping.
 *
 * <p>The message is for logs and tests; what reaches the learner is {@link #getCode()}, which
 * the UI turns into their own language.
 */
public class WordLineParseException extends RuntimeException {

    private final WordImportError code;

    public WordLineParseException(WordImportError code, String message) {
        super(message);
        this.code = code;
    }

    public WordImportError getCode() {
        return code;
    }
}
