package org.aventyrs.api.scene.dto;

/** Outbound broadcast on {@code /topic/scenes/{sceneId}/moves} after a move is accepted. */
public record TokenMovedEvent(
        String characterSheetId,
        GridPositionDto position
) {
}
