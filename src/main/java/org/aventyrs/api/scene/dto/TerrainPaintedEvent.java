package org.aventyrs.api.scene.dto;

import java.util.List;

/**
 * Every Terreno Difícil hex the scene now has — the whole set, so a client simply replaces its own — and, since core
 * 0.1.6.16, every painted terrain cell, whole as well.
 */
public record TerrainPaintedEvent(
        List<GridPositionDto> difficultTerrain,
        List<TerrainCellDto> terrainCells
) {

    /** Plain Terreno Difícil only. */
    public TerrainPaintedEvent(final List<GridPositionDto> difficultTerrain) {
        this(difficultTerrain, List.of());
    }
}
