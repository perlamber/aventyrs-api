package org.aventyrs.api.scene.dto;

import org.aventyrs.core.character.CharacterStatus;

/**
 * Wire shape of a persisted participant combat-state change — broadcast on {@code
 * /topic/scenes/{sceneId}/status} to every client watching the scene, including the one that sent
 * the {@link CharacterStatusMessage} that produced it (its own token badge is confirmed by the
 * echo, the same single-source-of-truth flow {@link TokenMovedEvent} uses for moves).
 */
public record CharacterStatusChangedEvent(String characterSheetId, int hitPointsSpent, CharacterStatus status) {
}
