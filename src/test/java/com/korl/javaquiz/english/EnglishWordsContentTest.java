package com.korl.javaquiz.english;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the bundled vocabulary. The Flyway migrations that load it only ever get one attempt
 * against a real database, so a malformed entry has to fail here instead of on a deploy.
 *
 * <p>Every bundled file is checked together, since they all land in the same two tables: a code
 * repeated across files would break the unique constraint on {@code word_groups.code} exactly as
 * one repeated inside a file would. Files come in three shapes — the ones that declare groups,
 * the ones that name an existing group and add to it, and the one that folds groups into another
 * — and the word-level checks run over the result, because that is what a group actually holds
 * once every migration has run.
 */
class EnglishWordsContentTest {

    private static final String CORPUS = "/content/english/words.json";
    private static final String PART_TWO = "/content/english/words-2026-part-2.json";
    private static final String PART_TWO_EXTRA = "/content/english/words-2026-part-2-extra.json";
    private static final String PERSONALITY = "/content/english/words-personality.json";
    private static final String PART_TWO_MERGE = "/content/english/words-2026-part-2-merge.json";
    private static final String UNIT_1D = "/content/english/words-unit-1d.json";

    private static JsonNode corpus;
    private static JsonNode partTwo;
    private static JsonNode partTwoExtra;
    private static JsonNode personality;
    private static JsonNode partTwoMerge;
    private static JsonNode unitOneD;

    @BeforeAll
    static void load() throws Exception {
        corpus = read(CORPUS);
        partTwo = read(PART_TWO);
        partTwoExtra = read(PART_TWO_EXTRA);
        personality = read(PERSONALITY);
        partTwoMerge = read(PART_TWO_MERGE);
        unitOneD = read(UNIT_1D);
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
        personality.get("groups").forEach(groups::add);
        unitOneD.get("groups").forEach(groups::add);
        return groups;
    }

    /**
     * Each group's words once every migration has run, keyed by the group code: the additions
     * appended, and the merged groups folded into their target and gone.
     */
    private static Map<String, List<JsonNode>> wordsByGroup() {
        Map<String, List<JsonNode>> words = new LinkedHashMap<>();
        for (JsonNode group : allGroups()) {
            List<JsonNode> into = words.computeIfAbsent(group.path("code").asText(), key -> new ArrayList<>());
            group.get("words").forEach(into::add);
        }
        for (JsonNode addition : List.of(partTwoExtra)) {
            List<JsonNode> into = words.get(addition.path("groupCode").asText());
            assertThat(into).describedAs("group %s the additions extend", addition.path("groupCode").asText())
                    .isNotNull();
            addition.get("words").forEach(into::add);
        }
        for (JsonNode merge : List.of(partTwoMerge)) {
            List<JsonNode> into = words.get(merge.path("groupCode").asText());
            assertThat(into).describedAs("group %s the merge folds into", merge.path("groupCode").asText())
                    .isNotNull();
            for (JsonNode code : merge.get("mergeFrom")) {
                List<JsonNode> from = words.remove(code.asText());
                assertThat(from).describedAs("group %s the merge folds in", code.asText()).isNotNull();
                into.addAll(from);
            }
        }
        return words;
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
        wordsByGroup().forEach((code, words) -> {
            for (JsonNode word : words) {
                String text = word.path("text").asText("");
                if (text.isBlank()) {
                    problems.add(code + ": a word with no English side");
                } else if (word.path("translation").asText("").isBlank()) {
                    problems.add(code + " / " + text + ": no translation");
                }
            }
        });
        assertThat(problems).isEmpty();
    }

    /**
     * The same word twice in one group is a paste accident; across groups it is deliberate. The
     * group is the merged one, so a phrase that the additions or a folded-in group repeat from
     * what the group already holds is caught here rather than showing up twice in the trainer.
     */
    @Test
    void noGroupRepeatsAWord() {
        List<String> duplicates = new ArrayList<>();
        wordsByGroup().forEach((code, words) -> {
            Set<String> seen = new HashSet<>();
            for (JsonNode word : words) {
                String text = word.path("text").asText("").toLowerCase();
                if (!seen.add(text)) {
                    duplicates.add(code + " / " + text);
                }
            }
        });
        assertThat(duplicates).isEmpty();
    }

    @Test
    void answerCountsAreNeverNegative() {
        for (List<JsonNode> words : wordsByGroup().values()) {
            for (JsonNode word : words) {
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

    /** The handout V14 loads: one group, and every row of it. */
    @Test
    void carriesTheTwentyTwentySixHandout() {
        assertThat(partTwo.get("groups")).hasSize(1);
        JsonNode group = partTwo.get("groups").get(0);
        assertThat(group.path("code").asText()).isEqualTo("seed-2026-part-2");
        assertThat(group.path("title").asText()).isEqualTo("2026 part 2 words");
        assertThat(group.get("words")).hasSize(42);
    }

    /**
     * What V17 appends to that group: the tail of Wordlist Unit 1A and both tables of the 1.2
     * Project Management handout. It names a group instead of declaring one — the group already
     * exists in every database V14 ran against — so the code it names has to resolve.
     */
    @Test
    void extendsTheTwentyTwentySixHandout() {
        assertThat(partTwoExtra.path("groupCode").asText()).isEqualTo("seed-2026-part-2");
        assertThat(partTwoExtra.get("words")).hasSize(44);
    }

    /**
     * The personality lesson V36 loads, as the two groups the handout itself keeps apart: the
     * trait vocabulary, and the verb + noun collocations drilled alongside it.
     */
    @Test
    void carriesThePersonalityLesson() {
        assertThat(personality.get("groups")).hasSize(2);
        JsonNode traits = personality.get("groups").get(0);
        JsonNode collocations = personality.get("groups").get(1);
        assertThat(traits.path("code").asText()).isEqualTo("seed-personality-traits");
        assertThat(traits.get("words")).hasSize(22);
        assertThat(collocations.path("code").asText()).isEqualTo("seed-verb-noun-collocations");
        assertThat(collocations.get("words")).hasSize(16);
    }

    /**
     * What V37 then does with those two groups: folds them into the 2026 one, which is therefore
     * where their words live once every migration has run — 42 from V14, 44 from V17, 22 and 16
     * from V36 — while the groups V36 declared are gone.
     */
    @Test
    void foldsThePersonalityLessonIntoTheTwentyTwentySixGroup() {
        assertThat(partTwoMerge.path("groupCode").asText()).isEqualTo("seed-2026-part-2");
        assertThat(partTwoMerge.get("mergeFrom")).extracting(JsonNode::asText)
                .containsExactly("seed-personality-traits", "seed-verb-noun-collocations");
        Map<String, List<JsonNode>> words = wordsByGroup();
        assertThat(words.get("seed-2026-part-2")).hasSize(124);
        assertThat(words).doesNotContainKeys("seed-personality-traits", "seed-verb-noun-collocations");
    }

    /** The Wordlist Unit 1D handout V38 loads: one group of its own, named by date and file. */
    @Test
    void carriesTheUnitOneDHandout() {
        assertThat(unitOneD.get("groups")).hasSize(1);
        JsonNode group = unitOneD.get("groups").get(0);
        assertThat(group.path("code").asText()).isEqualTo("seed-wordlist-unit-1d-011026");
        assertThat(group.path("title").asText()).isEqualTo("01.10.26 Wordlist_Unit 1D");
        assertThat(group.get("words")).hasSize(54);
    }
}
