package org.aventyrs.api.scene.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.aventyrs.core.scene.grid.GridPosition;

public record GridPositionDto(
        @Min(0) @Max(GridPosition.GRID_SIZE - 1) int x,
        @Min(0) @Max(GridPosition.GRID_SIZE - 1) int y
) {
}
