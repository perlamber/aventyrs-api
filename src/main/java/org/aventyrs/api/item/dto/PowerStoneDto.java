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
        PowerStoneImprovement improvement,
        Integer remainingCharges,
        Boolean lastChanceUsed
) {

    /** The shape before core 0.1.6.7 — {@code null} Cargas reads as a full stone. */
    public PowerStoneDto(PowerStoneType type, PowerStoneQuality quality, PowerStoneMasterpiece masterpiece,
                         PowerStoneImprovement improvement) {
        this(type, quality, masterpiece, improvement, null, null);
    }
}
