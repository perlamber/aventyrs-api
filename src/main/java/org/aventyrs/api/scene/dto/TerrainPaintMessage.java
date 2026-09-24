package org.aventyrs.api.scene.dto;

import jakarta.validation.Valid;

import java.util.List;

/**
 * The GM marks (difficult = true) or clears (false) Terreno Difícil on cells. Idempotent: marking
 * a marked hex or clearing a clear one changes nothing.
 */
public record TerrainPaintMessage(
        @Valid List<GridPositionDto> cells,
        boolean difficult
) {
}
