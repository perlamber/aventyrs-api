package org.aventyrs.api.scene.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** Inbound STOMP payload for {@code /app/scenes/{sceneId}/move} — move one participant's token. */
public record TokenMoveMessage(
        @NotBlank String characterSheetId,
        @Valid GridPositionDto position,
        Boolean sharesSpace
) {

    /** An ordinary move — the destination must be free. */
    public TokenMoveMessage(final String characterSheetId, final GridPositionDto position) {
        this(characterSheetId, position, null);
    }
}
