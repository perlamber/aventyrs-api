package org.aventyrs.api.scene.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Mirrors {@code org.aventyrs.core.scene.Scene}'s own "created empty" lifecycle — a new Scene
 * only needs a name; participants join afterward via update, same as core's {@code
 * addParticipant}. {@code width}/{@code height} size the playable grid and, like {@code terrain},
 * are fixed at creation — see {@code SceneDocument}'s javadoc for why. The 100 ceiling matches
 * {@code org.aventyrs.core.scene.grid.GridPosition#GRID_SIZE}.
 */
public record SceneCreateRequest(
        @NotBlank String name,
        @NotBlank String terrain,
        @Min(1) @Max(100) int width,
        @Min(1) @Max(100) int height
) {
}
