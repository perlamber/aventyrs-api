package org.aventyrs.api.scene.dto;

import java.util.List;

/**
 * Outbound broadcast on {@code /topic/scenes/{sceneId}/moves} after a move is accepted.
 *
 * @param elevation the token's elevation in UD now (core 0.1.6.16), 0 at the baseline
 * @param path      the route the mover reported, or {@code null}
 */
public record TokenMovedEvent(
        String characterSheetId,
        GridPositionDto position,
        int elevation,
        List<GridPositionDto> path
) {

    /** A move with no elevation or route. */
    public TokenMovedEvent(final String characterSheetId, final GridPositionDto position) {
        this(characterSheetId, position, 0, null);
    }
}
