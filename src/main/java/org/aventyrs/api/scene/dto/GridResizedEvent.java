package org.aventyrs.api.scene.dto;

/** Outbound broadcast on {@code /topic/scenes/{sceneId}/grid} after a resize is accepted — the
 * extent every client should now be drawing the board at. */
public record GridResizedEvent(
        int width,
        int height
) {
}
