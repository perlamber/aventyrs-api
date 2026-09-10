package org.aventyrs.api.item.dto;

import jakarta.validation.constraints.NotNull;
import org.aventyrs.core.character.DefenseType;
import org.aventyrs.core.item.DefensiveImprovement;
import org.aventyrs.core.magic.ElementalType;

/** See {@code ItemImprovementEntry} for the persisted shape this mirrors. */
public record ItemImprovementDto(
        @NotNull DefensiveImprovement definition,
        DefenseType selectedDefense,
        ElementalType selectedElementalType
) {
}
