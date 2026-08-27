package org.aventyrs.api.scene.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Inbound STOMP payload for {@code /app/scenes/{sceneId}/grid} — redraw the Scene's board at a new
 * extent, in hex cells. The 100 ceiling matches {@code
 * org.aventyrs.core.scene.grid.GridPosition#GRID_SIZE}, same as {@link SceneCreateRequest}'s.
 */
public record GridResizeMessage(
        @Min(1) @Max(100) int width,
        @Min(1) @Max(100) int height
) {
}
