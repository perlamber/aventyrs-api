package org.aventyrs.api.sheet.dto;

import jakarta.validation.constraints.Min;

/** {@code remainingRounds} is {@code null} for an open-ended variant. */
public record WitheringDto(
        @Min(0) int valuePerRound,
        @Min(0) Integer remainingRounds
) {
}
