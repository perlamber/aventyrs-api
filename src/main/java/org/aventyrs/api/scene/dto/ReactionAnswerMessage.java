package org.aventyrs.api.scene.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Inbound STOMP payload for {@code /app/scenes/{sceneId}/reaction-answers} — one reactor passes or, after resolving its
 * Reação, says it reacted (core 0.1.6.17).
 *
 * @param answer   {@code PASS} or {@code REACT}
 * @param reaction the core {@code GenericReaction} (or Título trait) name taken; {@code null} for a pass
 */
public record ReactionAnswerMessage(@NotBlank String windowId, @NotBlank String reactorCharacterSheetId,
                                    @NotBlank String answer, String reaction) {
}
