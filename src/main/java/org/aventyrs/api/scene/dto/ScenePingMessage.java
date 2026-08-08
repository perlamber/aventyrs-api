package org.aventyrs.api.scene.dto;

import jakarta.validation.Valid;

/** Inbound STOMP payload for {@code /app/scenes/{sceneId}/ping} — an ephemeral "sonar" marker, not
 * tied to any token and never persisted. */
public record ScenePingMessage(
        @Valid GridPositionDto position
) {
}
