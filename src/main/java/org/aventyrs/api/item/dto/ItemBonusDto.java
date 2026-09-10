package org.aventyrs.api.item.dto;

import jakarta.validation.constraints.NotNull;
import org.aventyrs.core.modifier.ModifierType;

/** See {@code ItemBonusEntry} for the persisted shape this mirrors. */
public record ItemBonusDto(
        @NotNull ModifierType modifierType,
        int value
) {
}
