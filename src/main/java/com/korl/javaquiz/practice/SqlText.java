package com.korl.javaquiz.practice;

/**
 * Lexical helpers for looking at submitted SQL without executing it.
 *
 * <p>Every check in {@link SqlGuard} runs against a <em>masked</em> copy of the statement:
 * comments and literals are blanked out so that a semicolon or keyword inside a string can
 * never be mistaken for structure.
 */
final class SqlText {

    private SqlText() {
    }

    /**
     * Replaces the body of every comment, string literal and quoted identifier with spaces,
     * keeping the length and the position of everything else intact.
     */
    static String mask(String sql) {
        char[] out = sql.toCharArray();
        int i = 0;
        int n = out.length;
        while (i < n) {
            char c = out[i];
            if (c == '-' && i + 1 < n && out[i + 1] == '-') {
                while (i < n && out[i] != '\n') {
                    out[i++] = ' ';
                }
            } else if (c == '/' && i + 1 < n && out[i + 1] == '*') {
                out[i++] = ' ';
                out[i++] = ' ';
                while (i < n && !(out[i] == '*' && i + 1 < n && out[i + 1] == '/')) {
                    out[i++] = ' ';
                }
                // Unterminated comments are left to the parser to reject.
                if (i < n) {
                    out[i++] = ' ';
                    out[i++] = ' ';
                }
            } else if (c == '\'' || c == '"' || c == '`') {
                i = maskQuoted(out, i, c);
            } else if (c == '$') {
                int tagEnd = dollarTagEnd(out, i);
                i = tagEnd < 0 ? i + 1 : maskDollarQuoted(out, i, tagEnd);
            } else {
                i++;
            }
        }
        return new String(out);
    }

    /** Masks a {@code '...'} / {@code "..."} run, honouring the doubled-quote escape. */
    private static int maskQuoted(char[] out, int start, char quote) {
        int i = start + 1;
        int n = out.length;
        while (i < n) {
            if (out[i] == quote) {
                if (i + 1 < n && out[i + 1] == quote) {
                    out[i++] = ' ';
                    out[i++] = ' ';
                    continue;
                }
                return i + 1;
            }
            out[i++] = ' ';
        }
        return i;
    }

    /** Returns the index just past the closing {@code $} of a dollar-quote tag, or -1. */
    private static int dollarTagEnd(char[] out, int start) {
        int i = start + 1;
        while (i < out.length && (Character.isLetterOrDigit(out[i]) || out[i] == '_')) {
            i++;
        }
        return i < out.length && out[i] == '$' ? i + 1 : -1;
    }

    private static int maskDollarQuoted(char[] out, int start, int bodyStart) {
        String tag = new String(out, start, bodyStart - start);
        int close = new String(out).indexOf(tag, bodyStart);
        int end = close < 0 ? out.length : close + tag.length();
        for (int i = bodyStart; i < Math.min(close < 0 ? out.length : close, out.length); i++) {
            out[i] = ' ';
        }
        return end;
    }

    /** The first SQL word of the statement, upper-cased, or an empty string. */
    static String leadingKeyword(String masked) {
        int i = 0;
        int n = masked.length();
        while (i < n && (Character.isWhitespace(masked.charAt(i)) || masked.charAt(i) == '(')) {
            i++;
        }
        int start = i;
        while (i < n && Character.isLetter(masked.charAt(i))) {
            i++;
        }
        return masked.substring(start, i).toUpperCase();
    }

    /** True when the masked text holds a semicolon with anything but whitespace after it. */
    static boolean hasMultipleStatements(String masked) {
        int semicolon = masked.indexOf(';');
        return semicolon >= 0 && !masked.substring(semicolon + 1).isBlank();
    }
}
