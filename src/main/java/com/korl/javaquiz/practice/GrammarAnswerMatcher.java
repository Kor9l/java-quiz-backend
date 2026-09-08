package com.korl.javaquiz.practice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Decides whether a written answer says the same thing as one of the accepted ones.
 *
 * <p>This is the whole of grading on the grammar track, and the reason it is one class: the
 * other two tracks decide correctness by running something, and here there is nothing to run.
 * What takes its place is normalising both sides until only the part being taught is left, so
 * that a learner who got the grammar right is never told otherwise by a stray space or a
 * capital letter.
 *
 * <p>What is deliberately <em>not</em> normalised away is anything a grammar exercise might be
 * about. Word order survives, obviously; so do the words themselves, commas inside a sentence
 * and every apostrophe that changes a form. Contractions are not expanded either — {@code don't}
 * and {@code do not} are the same answer only in an exercise that is not about them, and no
 * bundled exercise turns on that yet. That rule arrives with the content that needs it, rather
 * than sitting here untested.
 */
public final class GrammarAnswerMatcher {

    /** How a shuffled prompt separates its chunks, as handouts print them. */
    private static final Pattern CHUNK_SEPARATOR = Pattern.compile("/");

    /**
     * Apostrophes a keyboard or a phone may produce, all folded to the plain one. A learner
     * whose phone turned {@code don't} into {@code don’t} wrote the right form.
     */
    private static final Pattern CURLY_APOSTROPHES = Pattern.compile("[‘’ʼ´`]");

    /** Sentence-final punctuation, dropped from both sides. See {@link #normalise}. */
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[.?!]+$");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private GrammarAnswerMatcher() {
    }

    /**
     * The chunks of a shuffled prompt. Empty chunks are dropped rather than reported: a prompt
     * with a stray separator is broken content, and the content test is where that is caught.
     */
    public static List<String> chunks(String sentence) {
        if (sentence == null || sentence.isBlank()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        for (String chunk : CHUNK_SEPARATOR.split(sentence)) {
            String collapsed = collapse(chunk);
            if (!collapsed.isEmpty()) {
                chunks.add(collapsed);
            }
        }
        return List.copyOf(chunks);
    }

    /** True when the answer matches any of the ones the exercise accepts for that blank. */
    public static boolean matches(List<String> accepted, String answer, boolean caseSensitive) {
        String normalised = normalise(answer, caseSensitive);
        if (normalised.isEmpty()) {
            return false;
        }
        return accepted.stream()
                .map(candidate -> normalise(candidate, caseSensitive))
                .anyMatch(normalised::equals);
    }

    /**
     * Whether the answer is built from the words the learner was given — none left out, none
     * added. Compared as a bag of words rather than of chunks, so a learner who split
     * {@code a long time} across the sentence still passes this check and is judged on their
     * order, which is what the exercise is about.
     */
    public static boolean usesGivenWords(List<String> chunks, String answer, boolean caseSensitive) {
        List<String> given = new ArrayList<>();
        for (String chunk : chunks) {
            given.addAll(words(chunk, caseSensitive));
        }
        List<String> written = new ArrayList<>(words(answer, caseSensitive));
        given.sort(null);
        written.sort(null);
        return given.equals(written);
    }

    /** The text as a list of words, normalised the same way a whole answer is. */
    public static List<String> words(String text, boolean caseSensitive) {
        String normalised = normalise(text, caseSensitive);
        if (normalised.isEmpty()) {
            return List.of();
        }
        return List.of(normalised.split(" "));
    }

    /**
     * Both sides of every comparison pass through here.
     *
     * <p>Sentence-final {@code .}, {@code ?} and {@code !} are dropped from both sides on
     * purpose. In a {@code WORD_ORDER} exercise the punctuation is not among the chunks, so a
     * learner assembling a question has nowhere to get the question mark from — and marking
     * them wrong for the one character they were not given is the clearest possible example of
     * grading something the exercise is not teaching.
     */
    public static String normalise(String text, boolean caseSensitive) {
        if (text == null) {
            return "";
        }
        String result = collapse(CURLY_APOSTROPHES.matcher(text).replaceAll("'"));
        result = TRAILING_PUNCTUATION.matcher(result).replaceAll("").trim();
        return caseSensitive ? result : result.toLowerCase(Locale.ROOT);
    }

    /** Trims the ends and reduces every run of whitespace inside to a single space. */
    private static String collapse(String text) {
        return WHITESPACE.matcher(text.trim()).replaceAll(" ");
    }
}
