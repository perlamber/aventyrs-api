package org.aventyrs.api.sheet.dto;

import jakarta.validation.constraints.Min;

/** {@code remainingRounds} is {@code null} for the open-ended Sangramento Maior variant. */
public record BleedingDto(
        @Min(0) int valuePerRound,
        @Min(0) Integer remainingRounds
) {
}
