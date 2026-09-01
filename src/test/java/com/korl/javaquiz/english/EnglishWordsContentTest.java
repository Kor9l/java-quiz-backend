package com.korl.javaquiz.english;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the bundled vocabulary. The Flyway migrations that load it only ever get one attempt
 * against a real database, so a malformed entry has to fail here instead of on a deploy.
 *
 * <p>Every bundled file is checked together, since they all land in the same two tables: a code
 * repeated across files would break the unique constraint on {@code word_groups.code} exactly as
 * one repeated inside a file would.
 */
class EnglishWordsContentTest {

    private static final String CORPUS = "/content/english/words.json";
    private static final String PART_TWO = "/content/english/words-2026-part-2.json";

    private static JsonNode corpus;
    private static JsonNode partTwo;

    @BeforeAll
    static void load() throws Exception {
        corpus = read(CORPUS);
        partTwo = read(PART_TWO);
    }

    private static JsonNode read(String resource) throws Exception {
        try (InputStream in = EnglishWordsContentTest.class.getResourceAsStream(resource)) {
            assertThat(in).describedAs("bundled vocabulary %s", resource).isNotNull();
            return new ObjectMapper().readTree(in);
        }
    }

    /** Every group of every bundled file, which is what the migrations insert between them. */
    private static List<JsonNode> allGroups() {
        List<JsonNode> groups = new ArrayList<>();
        corpus.get("groups").forEach(groups::add);
        partTwo.get("groups").forEach(groups::add);
        return groups;
    }

    @Test
    void groupsAreCodedAndOrderedUniquely() {
        Set<String> codes = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (JsonNode group : allGroups()) {
            String code = group.path("code").asText(null);
            assertThat(code).describedAs("group code").isNotBlank();
            assertThat(codes.add(code)).describedAs("duplicate group code %s", code).isTrue();
            assertThat(group.path("title").asText(null)).describedAs("title of %s", code).isNotBlank();
            assertThat(orders.add(group.path("order").asInt(-1)))
                    .describedAs("duplicate order in %s", code).isTrue();
        }
        assertThat(codes).isNotEmpty();
    }

    @Test
    void everyWordHasBothSides() {
        List<String> problems = new ArrayList<>();
        for (JsonNode group : allGroups()) {
            String code = group.path("code").asText();
            for (JsonNode word : group.get("words")) {
                String text = word.path("text").asText("");
                if (text.isBlank()) {
                    problems.add(code + ": a word with no English side");
                } else if (word.path("translation").asText("").isBlank()) {
                    problems.add(code + " / " + text + ": no translation");
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    /** The same word twice in one group is a paste accident; across groups it is deliberate. */
    @Test
    void noGroupRepeatsAWord() {
        List<String> duplicates = new ArrayList<>();
        for (JsonNode group : allGroups()) {
            Set<String> seen = new HashSet<>();
            for (JsonNode word : group.get("words")) {
                String text = word.path("text").asText("").toLowerCase();
                if (!seen.add(text)) {
                    duplicates.add(group.path("code").asText() + " / " + text);
                }
            }
        }
        assertThat(duplicates).isEmpty();
    }

    @Test
    void answerCountsAreNeverNegative() {
        for (JsonNode group : allGroups()) {
            for (JsonNode word : group.get("words")) {
                assertThat(word.path("correct").asInt(0))
                        .describedAs("correct count of %s", word.path("text").asText()).isNotNegative();
                assertThat(word.path("incorrect").asInt(0))
                        .describedAs("incorrect count of %s", word.path("text").asText()).isNotNegative();
            }
        }
    }

    /** The whole corpus the merge brought over, so a silent loss on a re-export gets noticed. */
    @Test
    void carriesTheWholeImportedCorpus() {
        int words = 0;
        for (JsonNode group : corpus.get("groups")) {
            words += group.get("words").size();
        }
        assertThat(corpus.get("groups")).hasSize(8);
        assertThat(words).isEqualTo(462);
    }

    /** The handout V12 loads: one group, and every row of it. */
    @Test
    void carriesTheTwentyTwentySixHandout() {
        assertThat(partTwo.get("groups")).hasSize(1);
        JsonNode group = partTwo.get("groups").get(0);
        assertThat(group.path("code").asText()).isEqualTo("seed-2026-part-2");
        assertThat(group.path("title").asText()).isEqualTo("2026 part 2 words");
        assertThat(group.get("words")).hasSize(42);
    }
}
