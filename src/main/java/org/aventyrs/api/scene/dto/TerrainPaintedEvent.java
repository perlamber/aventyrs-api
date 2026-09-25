package org.aventyrs.api.scene.dto;

import java.util.List;

/** Every Terreno Difícil hex the scene now has — the whole set, so a client simply replaces its own. */
public record TerrainPaintedEvent(
        List<GridPositionDto> difficultTerrain
) {
}
