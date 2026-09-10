package org.aventyrs.api.item.dto;

import jakarta.validation.constraints.NotNull;
import org.aventyrs.core.character.DefenseType;
import org.aventyrs.core.item.DefensiveMasterpiece;
import org.aventyrs.core.modifier.ModifierType;

/** See {@code ItemMasterpieceEntry} for the persisted shape this mirrors. */
public record ItemMasterpieceDto(
        @NotNull DefensiveMasterpiece definition,
        DefenseType selectedDefense,
        ModifierType selectedActionBonus
) {
}
