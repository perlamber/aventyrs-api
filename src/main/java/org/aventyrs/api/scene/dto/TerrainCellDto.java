package org.aventyrs.api.scene.dto;

/**
 * One painted hex on the wire (core 0.1.6.16): its {@code TerrainKind}, Terreno Difícil, ground level in UD and
 * climbing/swimming GD ({@code null} = Médio). As a paint template its {@code x}/{@code y} are ignored.
 */
public record TerrainCellDto(int x, int y, String kind, boolean difficult, int groundLevel, String difficulty) {

    /** Whether this is plain ground at the baseline — painting it clears a hex. */
    public boolean isPlain() {
        return (kind == null || "GROUND".equals(kind)) && !difficult && groundLevel == 0 && difficulty == null;
    }
}
