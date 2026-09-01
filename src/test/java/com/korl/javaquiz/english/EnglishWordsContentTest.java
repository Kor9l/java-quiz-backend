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
 * Guards the bundled vocabulary. The Flyway migration that loads it only ever gets one attempt
 * against a real database, so a malformed entry has to fail here instead of on a deploy.
 */
class EnglishWordsContentTest {

    private static JsonNode root;

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in = EnglishWordsContentTest.class.getResourceAsStream("/content/english/words.json")) {
            assertThat(in).describedAs("bundled English vocabulary").isNotNull();
            root = new ObjectMapper().readTree(in);
        }
    }

    @Test
    void groupsAreCodedAndOrderedUniquely() {
        Set<String> codes = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (JsonNode group : root.get("groups")) {
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
        for (JsonNode group : root.get("groups")) {
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
        for (JsonNode group : root.get("groups")) {
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
        for (JsonNode group : root.get("groups")) {
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
        for (JsonNode group : root.get("groups")) {
            words += group.get("words").size();
        }
        assertThat(root.get("groups")).hasSize(8);
        assertThat(words).isEqualTo(462);
    }
}
