package org.aventyrs.api.sheet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.aventyrs.core.character.EgoDomain;

public record CharacterSheetUpdateRequest(
        @NotNull @Valid CharacterDto character,
        @NotBlank String playerId,
        @NotNull @DecimalMin("0") BigDecimal totalExperience,
        @NotNull @DecimalMin("0") BigDecimal unUsedExperience,
        @Min(0) int hitPointsSpent,
        @Min(0) int magicPointsSpent,
        @Min(0) int determinationPointsSpent,
        @Min(0) int shieldPoints,
        @Min(0) int famaPositiva,
        @Min(0) int famaNegativa,
        Map<EgoDomain, @Min(0) Integer> temporaryEgoPoints,
        List<@Valid TemporaryBonusDto> temporaryBonuses
) {
}
