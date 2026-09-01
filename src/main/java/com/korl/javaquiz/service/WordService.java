package com.korl.javaquiz.service;

import com.korl.javaquiz.api.dto.WordImportRequest;
import com.korl.javaquiz.api.dto.WordRequest;
import com.korl.javaquiz.api.error.ApiException;
import com.korl.javaquiz.domain.Word;
import com.korl.javaquiz.domain.WordFavoriteRepository;
import com.korl.javaquiz.domain.WordGroup;
import com.korl.javaquiz.domain.WordGroupType;
import com.korl.javaquiz.domain.WordRepository;
import com.korl.javaquiz.english.ParsedWordLine;
import com.korl.javaquiz.english.WordImportError;
import com.korl.javaquiz.english.WordImportResult;
import com.korl.javaquiz.english.WordLineParseException;
import com.korl.javaquiz.english.WordLineParser;
import com.korl.javaquiz.security.UserPrincipal;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response.Status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Adding and managing vocabulary: the words themselves, their bulk import, and favourites. */
@ApplicationScoped
public class WordService {

    private final WordRepository words;
    private final WordFavoriteRepository favorites;
    private final WordGroupService groups;

    public WordService(WordRepository words, WordFavoriteRepository favorites, WordGroupService groups) {
        this.words = words;
        this.favorites = favorites;
        this.groups = groups;
    }

    /** The group list: every group the learner reaches, with its size and whether they may edit it. */
    @Transactional
    public List<Map<String, Object>> listGroups(UserPrincipal user) {
        Map<UUID, Long> counts = words.countByGroup(user.getId());
        List<Map<String, Object>> result = new ArrayList<>();
        for (WordGroup group : groups.listAccessible(user.getId())) {
            result.add(groupDto(user, group, counts.getOrDefault(group.getId(), 0L)));
        }
        return result;
    }

    /**
     * The whole vocabulary in one answer, grouped the way it is shown. One query for the words
     * and one for the favourites, rather than a round trip per group.
     */
    @Transactional
    public List<Map<String, Object>> listWordsByGroup(UserPrincipal user) {
        Set<UUID> favoriteIds = favorites.findWordIds(user.getId());
        Map<UUID, List<Word>> byGroup = new LinkedHashMap<>();
        for (Word word : words.findAccessible(user.getId())) {
            byGroup.computeIfAbsent(word.getGroupId(), key -> new ArrayList<>()).add(word);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (WordGroup group : groups.listAccessible(user.getId())) {
            List<Word> groupWords = byGroup.getOrDefault(group.getId(), List.of());
            Map<String, Object> dto = groupDto(user, group, groupWords.size());
            dto.put("words", wordDtos(groupWords, favoriteIds));
            result.add(dto);
        }
        return result;
    }

    /** One group with its words, which is what the group editing screen opens on. */
    @Transactional
    public Map<String, Object> group(UserPrincipal user, UUID groupId) {
        WordGroup group = groups.accessible(user.getId(), groupId);
        List<Word> groupWords = words.findByGroupId(groupId);
        Map<String, Object> dto = groupDto(user, group, groupWords.size());
        dto.put("words", wordDtos(groupWords, favorites.findWordIds(user.getId())));
        return dto;
    }

    @Transactional
    public Map<String, Object> create(UserPrincipal user, UUID groupId, WordRequest request) {
        groups.editable(user, groupId);
        Word word = new Word(
                groupId,
                words.nextSortOrder(groupId),
                request.text.strip(),
                request.translation.strip(),
                blankToNull(request.example),
                request.isNew,
                Instant.now());
        return wordDto(words.save(word), false);
    }

    @Transactional
    public Map<String, Object> update(UserPrincipal user, UUID wordId, WordRequest request) {
        Word word = accessible(user, wordId);
        groups.editable(user, word.getGroupId());
        word.edit(
                request.text.strip(),
                request.translation.strip(),
                blankToNull(request.example),
                request.isNew,
                Instant.now());
        return wordDto(words.save(word), favorites.contains(user.getId(), wordId));
    }

    @Transactional
    public void delete(UserPrincipal user, UUID wordId) {
        Word word = accessible(user, wordId);
        groups.editable(user, word.getGroupId());
        words.delete(word);
    }

    /**
     * A pasted list or a typed grid, added to one group. A line that will not parse is reported
     * and skipped rather than failing the import: the usual paste has a stray line or two in it,
     * and re-pasting the other forty is not a fix.
     */
    @Transactional
    public Map<String, Object> importWords(UserPrincipal user, WordImportRequest request) {
        WordGroup target = resolveTarget(user, request);
        WordImportResult result = new WordImportResult();
        int order = words.nextSortOrder(target.getId());
        Instant now = Instant.now();

        if ("TEXT".equalsIgnoreCase(request.mode)) {
            importLines(request.text, target.getId(), order, now, result);
        } else if ("TABLE".equalsIgnoreCase(request.mode)) {
            importRows(request.rows, target.getId(), order, now, result);
        } else {
            throw new ApiException(Status.BAD_REQUEST, "Unknown import mode: " + request.mode);
        }

        // A run that added nothing and explained nothing means the payload was empty, which is a
        // mistake worth saying out loud rather than answering "imported: 0".
        if (result.getImported() == 0 && result.getErrors().isEmpty()) {
            throw new ApiException(Status.BAD_REQUEST, "Nothing to import");
        }

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("groupId", target.getId());
        dto.put("groupTitle", target.getTitle());
        dto.put("imported", result.getImported());
        dto.put("errors", result.getErrors());
        return dto;
    }

    @Transactional
    public Map<String, Object> toggleFavorite(UUID userId, UUID wordId) {
        words.findAccessibleById(userId, wordId)
                .orElseThrow(() -> new ApiException(Status.NOT_FOUND, "Word not found"));
        boolean favorite = !favorites.contains(userId, wordId);
        if (favorite) {
            favorites.add(userId, wordId);
        } else {
            favorites.remove(userId, wordId);
        }
        return Map.of("favorite", favorite);
    }

    private void importLines(String rawText, UUID groupId, int firstOrder, Instant now, WordImportResult result) {
        if (rawText == null || rawText.isBlank()) {
            return;
        }
        int order = firstOrder;
        String[] lines = rawText.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            try {
                Optional<ParsedWordLine> parsed = WordLineParser.parseLine(lines[i]);
                if (parsed.isEmpty()) {
                    continue;
                }
                ParsedWordLine line = parsed.get();
                words.save(new Word(groupId, order++, line.text(), line.translation(), null,
                        line.markedNew(), now));
                result.countImported();
            } catch (WordLineParseException e) {
                result.addError(i + 1, e.getCode());
            }
        }
    }

    private void importRows(List<WordImportRequest.Row> rows, UUID groupId, int firstOrder, Instant now,
                            WordImportResult result) {
        if (rows == null) {
            return;
        }
        int order = firstOrder;
        for (int i = 0; i < rows.size(); i++) {
            WordImportRequest.Row row = rows.get(i);
            String text = row == null ? null : blankToNull(row.text);
            String translation = row == null ? null : blankToNull(row.translation);
            String example = row == null ? null : blankToNull(row.example);
            if (text == null && translation == null && example == null) {
                continue;
            }
            if (text == null || translation == null) {
                result.addError(i + 1, WordImportError.MISSING_FIELDS);
                continue;
            }
            words.save(new Word(groupId, order++, text, translation, example, false, now));
            result.countImported();
        }
    }

    /** Either an existing group the caller may write to, or a new personal one made on the spot. */
    private WordGroup resolveTarget(UserPrincipal user, WordImportRequest request) {
        if (request.newGroupTitle != null && !request.newGroupTitle.isBlank()) {
            return groups.create(user.getId(), request.newGroupTitle);
        }
        if (request.groupId == null) {
            throw new ApiException(Status.BAD_REQUEST, "Choose an existing group or name a new one");
        }
        return groups.editable(user, request.groupId);
    }

    private Word accessible(UserPrincipal user, UUID wordId) {
        return words.findAccessibleById(user.getId(), wordId)
                .orElseThrow(() -> new ApiException(Status.NOT_FOUND, "Word not found"));
    }

    private Map<String, Object> groupDto(UserPrincipal user, WordGroup group, long wordCount) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", group.getId());
        dto.put("code", group.getCode());
        dto.put("title", group.getTitle());
        dto.put("type", group.getGroupType().name());
        dto.put("owned", group.getGroupType() == WordGroupType.PERSONAL
                && user.getId().equals(group.getOwnerId()));
        dto.put("editable", groups.canEdit(user, group));
        dto.put("wordCount", wordCount);
        return dto;
    }

    private List<Map<String, Object>> wordDtos(List<Word> source, Set<UUID> favoriteIds) {
        List<Map<String, Object>> result = new ArrayList<>(source.size());
        for (Word word : source) {
            result.add(wordDto(word, favoriteIds.contains(word.getId())));
        }
        return result;
    }

    private Map<String, Object> wordDto(Word word, boolean favorite) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", word.getId());
        dto.put("groupId", word.getGroupId());
        dto.put("text", word.getText());
        dto.put("translation", word.getTranslation());
        dto.put("example", word.getExample());
        dto.put("isNew", word.isNew());
        dto.put("correctCount", word.getCorrectCount());
        dto.put("incorrectCount", word.getIncorrectCount());
        dto.put("favorite", favorite);
        return dto;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
