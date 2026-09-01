package com.korl.javaquiz.english;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the format vocabulary lists actually get pasted in:
 *
 * <pre>
 * 1 to go global — выйти на мировой уровень
 * 2 * a gap in the market – ниша на рынке
 * ongoing — текущий
 * </pre>
 *
 * A leading number is a list marker and is dropped; a {@code *} after it means the learner has
 * just met the word. The number may be decimal ({@code 1.2 word}) but must not be followed by a
 * bare dot — {@code 1. word} keeps the {@code 1.} as part of the English side, which is how the
 * app this came from behaved.
 *
 * <p>English and translation are separated by a dash — em or en, spaced or not. A plain hyphen
 * is not a separator: too many entries contain one.
 */
public final class WordLineParser {

    private static final Pattern LIST_MARKER = Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*(\\*)?\\s+(.+)$");

    private static final String[] SEPARATORS = {" \u2014 ", " \u2013 ", "\u2014", "\u2013"};

    private WordLineParser() {
    }

    /** Empty for a blank line, which an import skips rather than reports. */
    public static Optional<ParsedWordLine> parseLine(String rawLine) {
        String line = rawLine == null ? "" : rawLine.strip();
        if (line.isEmpty()) {
            return Optional.empty();
        }
        boolean markedNew = false;
        String payload = line;
        Matcher marker = LIST_MARKER.matcher(line);
        if (marker.matches()) {
            markedNew = marker.group(2) != null;
            payload = marker.group(3).strip();
        }
        for (String separator : SEPARATORS) {
            int at = payload.indexOf(separator);
            if (at < 0) {
                continue;
            }
            String text = payload.substring(0, at).strip();
            String translation = payload.substring(at + separator.length()).strip();
            if (text.isEmpty() || translation.isEmpty()) {
                throw new WordLineParseException(WordImportError.EMPTY_SIDE,
                        "Empty English or translation side");
            }
            return Optional.of(new ParsedWordLine(text, translation, markedNew));
        }
        throw new WordLineParseException(WordImportError.MISSING_SEPARATOR,
                "Missing em/en dash between English and translation");
    }
}
