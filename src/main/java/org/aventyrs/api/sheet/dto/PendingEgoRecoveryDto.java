package org.aventyrs.api.sheet.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.aventyrs.core.character.EgoDomain;
import org.aventyrs.core.rest.RestType;

public record PendingEgoRecoveryDto(
        @NotNull EgoDomain domain,
        @Min(0) int value,
        @NotNull RestType minimumRestType
) {
}
