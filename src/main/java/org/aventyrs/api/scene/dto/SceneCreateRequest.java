package org.aventyrs.api.scene.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Mirrors {@code org.aventyrs.core.scene.Scene}'s own "created empty" lifecycle — a new Scene
 * only needs a name; participants join afterward via update, same as core's {@code
 * addParticipant}.
 */
public record SceneCreateRequest(@NotBlank String name) {
}
