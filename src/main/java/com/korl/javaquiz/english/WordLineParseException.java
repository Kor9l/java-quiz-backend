package com.korl.javaquiz.english;

/** One unusable line. Thrown per line so an import reports them all instead of stopping. */
public class WordLineParseException extends RuntimeException {

    public WordLineParseException(String message) {
        super(message);
    }
}
