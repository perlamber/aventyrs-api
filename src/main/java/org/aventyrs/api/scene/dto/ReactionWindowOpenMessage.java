package org.aventyrs.api.scene.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Inbound STOMP payload for {@code /app/scenes/{sceneId}/reaction-windows} — the acting client pauses its action where
 * it provoked Reações (core 0.1.6.17) and names who may answer. Opening one replaces any window still open.
 */
public record ReactionWindowOpenMessage(
        @NotBlank String kind,
        @NotBlank String actorCharacterSheetId,
        List<String> reactorCharacterSheetIds,
        GridPositionDto position,
        int elevation,
        String description
) {
}
