package com.korl.javaquiz.userstate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProgressPayload {

    public Map<String, SectionProgress> sections = new LinkedHashMap<>();

    public static class SectionProgress {
        public Instant readAt;
        public int readCount;
        public int wrongCountAtRead;
    }

    public SectionProgress get(String topicId, String sectionId) {
        return sections.get(topicId + "/" + sectionId);
    }

    public Instant readAt(String topicId, String sectionId) {
        SectionProgress progress = get(topicId, sectionId);
        return progress == null ? null : progress.readAt;
    }

    public boolean isRead(String topicId, String sectionId) {
        return readAt(topicId, sectionId) != null;
    }

    public void markRead(String topicId, String sectionId, Instant when, int wrongBaseline) {
        SectionProgress progress = sections.computeIfAbsent(topicId + "/" + sectionId, k -> new SectionProgress());
        progress.readAt = when;
        progress.readCount++;
        progress.wrongCountAtRead = wrongBaseline;
    }

    public void markUnread(String topicId, String sectionId) {
        sections.remove(topicId + "/" + sectionId);
    }

    public int readSectionCount() {
        return (int) sections.values().stream().filter(p -> p.readAt != null).count();
    }

    public void reset() {
        sections = new LinkedHashMap<>();
    }
}
