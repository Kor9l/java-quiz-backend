package com.korl.javaquiz.english;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * The parser is the only part of a bulk import a learner can get wrong, so the shapes their
 * lists actually arrive in are pinned here rather than discovered on a bad paste.
 */
class WordLineParserTest {

    @Test
    void readsAPlainDashedLine() {
        ParsedWordLine line = parse("ongoing — текущий");

        assertThat(line.text()).isEqualTo("ongoing");
        assertThat(line.translation()).isEqualTo("текущий");
        assertThat(line.markedNew()).isFalse();
    }

    @Test
    void dropsALeadingListNumber() {
        assertThat(parse("12 to go global — выйти на мировой уровень").text()).isEqualTo("to go global");
        assertThat(parse("1.2 to go global — выйти на мировой уровень").text()).isEqualTo("to go global");
    }

    /** A bare dot after the number is not part of the format, and stays with the word. */
    @Test
    void keepsANumberFollowedByADot() {
        assertThat(parse("12. to go global — выйти на мировой уровень").text())
                .isEqualTo("12. to go global");
    }

    @Test
    void aStarAfterTheNumberMarksTheWordAsNew() {
        assertThat(parse("3* a start-up — стартап").markedNew()).isTrue();
        assertThat(parse("3 * a start-up — стартап").markedNew()).isTrue();
        assertThat(parse("3 a start-up — стартап").markedNew()).isFalse();
    }

    @Test
    void acceptsAnEnDashAndAnUnspacedDash() {
        assertThat(parse("a pop-up – лавка").translation()).isEqualTo("лавка");
        assertThat(parse("a pop-up—лавка").translation()).isEqualTo("лавка");
    }

    /** Hyphens are all over the English side, so they cannot double as the separator. */
    @Test
    void keepsHyphensInsideTheWord() {
        ParsedWordLine line = parse("a would-be entrepreneur — будущий предприниматель");

        assertThat(line.text()).isEqualTo("a would-be entrepreneur");
        assertThat(line.translation()).isEqualTo("будущий предприниматель");
    }

    @Test
    void splitsOnTheFirstDashOnly() {
        ParsedWordLine line = parse("to take over — взять под контроль — принимать на себя");

        assertThat(line.text()).isEqualTo("to take over");
        assertThat(line.translation()).isEqualTo("взять под контроль — принимать на себя");
    }

    @Test
    void skipsBlankLines() {
        assertThat(WordLineParser.parseLine("   ")).isEmpty();
        assertThat(WordLineParser.parseLine("")).isEmpty();
        assertThat(WordLineParser.parseLine(null)).isEmpty();
    }

    /**
     * The code is what the UI translates, so it is the part worth pinning — the message beside
     * it only ever reaches a log.
     */
    @Test
    void rejectsALineWithoutASeparator() {
        assertThat(codeOf("just some words")).isEqualTo(WordImportError.MISSING_SEPARATOR);
    }

    @Test
    void rejectsAnEmptySide() {
        assertThat(codeOf("ongoing — ")).isEqualTo(WordImportError.EMPTY_SIDE);
        assertThat(codeOf(" — текущий")).isEqualTo(WordImportError.EMPTY_SIDE);
    }

    private static WordImportError codeOf(String line) {
        Throwable thrown = catchThrowable(() -> WordLineParser.parseLine(line));
        assertThat(thrown).describedAs(line).isInstanceOf(WordLineParseException.class);
        return ((WordLineParseException) thrown).getCode();
    }

    private static ParsedWordLine parse(String line) {
        Optional<ParsedWordLine> parsed = WordLineParser.parseLine(line);
        assertThat(parsed).describedAs(line).isPresent();
        return parsed.get();
    }
}
