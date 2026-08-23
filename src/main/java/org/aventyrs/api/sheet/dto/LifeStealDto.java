package org.aventyrs.api.sheet.dto;

import jakarta.validation.constraints.Min;

/** {@code value} isn't per-round — see {@code LifeStealEntry}'s own javadoc. */
public record LifeStealDto(
        @Min(0) int value,
        @Min(0) Integer remainingRounds
) {
}
