package org.aventyrs.api.sheet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.aventyrs.api.item.dto.InventoryItemDto;
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
        @Min(0) int equipmentPoints,
        Map<EgoDomain, @Min(0) Integer> temporaryEgoPoints,
        List<@Valid TemporaryBonusDto> temporaryBonuses,
        List<@Valid BleedingDto> bleedingEffects,
        List<@Valid ManaDrainDto> manaDrains,
        List<@Valid WitheringDto> witheringEffects,
        List<@Valid PendingEgoRecoveryDto> pendingEgoRecoveries,
        List<@Valid LifeStealDto> lifeSteals,
        List<@Valid InventoryItemDto> inventory,
        String tokenImageUrl,
        /** Core 0.0.76's Ego state beyond the temporary spend. {@code null} leaves the stored one alone. */
        @Valid EgoLedgerDto egoLedger
) {

    /** A request from a client before the Ego ledger — the stored one is left alone. */
    public CharacterSheetUpdateRequest(CharacterDto character, String playerId, BigDecimal totalExperience,
            BigDecimal unUsedExperience, int hitPointsSpent, int magicPointsSpent, int determinationPointsSpent,
            int shieldPoints, int famaPositiva, int famaNegativa, int equipmentPoints,
            Map<EgoDomain, Integer> temporaryEgoPoints, List<TemporaryBonusDto> temporaryBonuses,
            List<BleedingDto> bleedingEffects, List<ManaDrainDto> manaDrains, List<WitheringDto> witheringEffects,
            List<PendingEgoRecoveryDto> pendingEgoRecoveries, List<LifeStealDto> lifeSteals,
            List<InventoryItemDto> inventory, String tokenImageUrl) {
        this(character, playerId, totalExperience, unUsedExperience, hitPointsSpent, magicPointsSpent,
                determinationPointsSpent, shieldPoints, famaPositiva, famaNegativa, equipmentPoints, temporaryEgoPoints,
                temporaryBonuses, bleedingEffects, manaDrains, witheringEffects, pendingEgoRecoveries, lifeSteals,
                inventory, tokenImageUrl, null);
    }
}
