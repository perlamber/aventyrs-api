package org.aventyrs.api.scene.dto;

import jakarta.validation.Valid;
import java.util.List;

/**
 * The GM marks (difficult = true) or clears (false) Terreno Difícil on cells. Idempotent: marking
 * a marked hex or clearing a clear one changes nothing.
 *
 * <p>With a {@code template} (core 0.1.6.16) the cells are painted with that terrain instead — water, a climbable
 * face, an obstacle, a ground level — and {@code difficult} is ignored; a plain template ({@link
 * TerrainCellDto#isPlain()}) clears them back to ground.
 */
public record TerrainPaintMessage(
        @Valid List<GridPositionDto> cells,
        boolean difficult,
        TerrainCellDto template
) {

    /** Marking or clearing plain Terreno Difícil — every paint before core 0.1.6.16. */
    public TerrainPaintMessage(final List<GridPositionDto> cells, final boolean difficult) {
        this(cells, difficult, null);
    }
}
