package org.aventyrs.api.sheet.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.aventyrs.core.modifier.ModifierType;

public record TemporaryBonusDto(
        @NotNull ModifierType type,
        int value,
        @Min(0) int remainingRounds
) {
}
