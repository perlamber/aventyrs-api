package org.aventyrs.api.item.dto;

import jakarta.validation.constraints.NotNull;
import org.aventyrs.core.item.PowerStoneImprovement;
import org.aventyrs.core.item.PowerStoneMasterpiece;
import org.aventyrs.core.item.PowerStoneQuality;
import org.aventyrs.core.item.PowerStoneType;

/** See {@code PowerStoneEntry} for the persisted shape this mirrors. */
public record PowerStoneDto(
        @NotNull PowerStoneType type,
        @NotNull PowerStoneQuality quality,
        PowerStoneMasterpiece masterpiece,
        PowerStoneImprovement improvement
) {
}
