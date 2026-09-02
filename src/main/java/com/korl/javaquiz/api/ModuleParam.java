package com.korl.javaquiz.api;

import com.korl.javaquiz.api.error.ApiException;
import com.korl.javaquiz.domain.LearningModule;
import jakarta.ws.rs.core.Response.Status;

/**
 * Reads the {@code ?module=} query parameter the same way everywhere it appears.
 *
 * <p>Absent means backend, which is what every caller written before grammar existed meant by
 * leaving it out. An unknown name is a 400 rather than a silent fall back: a client asking for a
 * module this build does not have wants to hear about it, not to be handed the other one's
 * content and left wondering why nothing matches.
 */
final class ModuleParam {

    private ModuleParam() {
    }

    static LearningModule orBackend(String value) {
        if (value == null || value.isBlank()) {
            return LearningModule.BACKEND;
        }
        return LearningModule.parse(value)
                .orElseThrow(() -> new ApiException(Status.BAD_REQUEST, "Unknown module: " + value));
    }
}
