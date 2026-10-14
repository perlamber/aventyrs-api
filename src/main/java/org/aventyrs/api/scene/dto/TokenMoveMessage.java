package org.aventyrs.api.scene.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Inbound STOMP payload for {@code /app/scenes/{sceneId}/move} — move one participant's token.
 *
 * @param elevation the token's elevation in UD after the move (core 0.1.6.16); {@code null} keeps the one it had
 * @param path      the hexes stepped through, origin excluded (core 0.1.6.16) — relayed so every board can show the
 *                  route; never validated here, the same client-side boundary as the destination
 */
public record TokenMoveMessage(
        @NotBlank String characterSheetId,
        @Valid GridPositionDto position,
        Boolean sharesSpace,
        Integer elevation,
        @Valid List<GridPositionDto> path
) {

    /** An ordinary move — the destination must be free. */
    public TokenMoveMessage(final String characterSheetId, final GridPositionDto position) {
        this(characterSheetId, position, null);
    }

    /** A move from before core 0.1.6.16 — no elevation, no route. */
    public TokenMoveMessage(final String characterSheetId, final GridPositionDto position, final Boolean sharesSpace) {
        this(characterSheetId, position, sharesSpace, null, null);
    }
}
