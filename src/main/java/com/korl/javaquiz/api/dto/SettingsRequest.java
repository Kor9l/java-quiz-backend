package com.korl.javaquiz.api.dto;

import com.korl.javaquiz.domain.Language;
import com.korl.javaquiz.domain.Level;

import java.util.List;

/**
 * Body of {@code PUT /api/settings}. Every field is optional and boxed on purpose: with
 * primitives an absent flag would be indistinguishable from {@code false} and a partial
 * update would silently switch options off.
 */
public class SettingsRequest {

    public Language language;
    public Level level;
    public List<String> selectedTopics;
    public Integer questionCount;
    public Boolean infiniteMode;
    public Boolean shuffleOptions;
    public Boolean smartSelection;
    public Boolean showExplanation;
    public Boolean darkTheme;
}
