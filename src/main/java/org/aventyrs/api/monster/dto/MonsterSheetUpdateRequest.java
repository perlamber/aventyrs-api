package org.aventyrs.api.monster.dto;

import org.aventyrs.api.item.dto.InventoryItemDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.aventyrs.api.sheet.dto.BleedingDto;
import org.aventyrs.api.sheet.dto.LifeStealDto;
import org.aventyrs.api.sheet.dto.ManaDrainDto;
import org.aventyrs.api.sheet.dto.PendingEgoRecoveryDto;
import org.aventyrs.api.sheet.dto.TemporaryBonusDto;
import org.aventyrs.api.sheet.dto.WitheringDto;
import org.aventyrs.core.character.EgoDomain;

public record MonsterSheetUpdateRequest(
        @NotNull @Valid MonsterBlueprintDto blueprint,
        @NotBlank String playerId,
        @Min(0) int hitPointsSpent,
        @Min(0) int magicPointsSpent,
        @Min(0) int determinationPointsSpent,
        @Min(0) int shieldPoints,
        Map<EgoDomain, @Min(0) Integer> temporaryEgoPoints,
        List<@Valid TemporaryBonusDto> temporaryBonuses,
        List<@Valid BleedingDto> bleedingEffects,
        List<@Valid ManaDrainDto> manaDrains,
        List<@Valid WitheringDto> witheringEffects,
        List<@Valid PendingEgoRecoveryDto> pendingEgoRecoveries,
        List<@Valid LifeStealDto> lifeSteals,
        List<@Valid InventoryItemDto> inventory,
        String tokenImageUrl
) {
}
