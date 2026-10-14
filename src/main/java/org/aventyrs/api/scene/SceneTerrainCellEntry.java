package org.aventyrs.api.scene;

/**
 * One hex the GM painted with terrain (core 0.1.6.16, {@code scene.grid.TerrainCell}) — persisted on the scene.
 *
 * @param kind        a core {@code TerrainKind} name
 * @param difficulty  a core {@code DifficultyLevel} name, or {@code null} for the default Médio
 */
public record SceneTerrainCellEntry(int x, int y, String kind, boolean difficult, int groundLevel,
                                    String difficulty) {
}
