package org.aventyrs.api.sheet.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.aventyrs.api.item.dto.InventoryItemDto;
import org.aventyrs.core.character.EgoDomain;

/**
 * {@code campaignId} is {@code null} for a sheet in no Campanha. {@code progressionLocked} is true
 * while that Campanha has an ONGOING Sessão, and the client disables progression (Talentos,
 * Graduações, Títulos, Especializações, Habilidades, Atributos) while it holds. That check is
 * client-only by design: {@code PUT /character-sheets/{id}} does not refuse progression changes.
 */
public record CharacterSheetResponse(
        String id,
        CharacterResponse character,
        String playerId,
        BigDecimal totalExperience,
        BigDecimal unUsedExperience,
        int damageTaken,
        int manaSpent,
        int determinationSpent,
        int shieldPoints,
        int famaPositiva,
        int famaNegativa,
        int equipmentPoints,
        Map<EgoDomain, Integer> temporaryEgoPoints,
        List<TemporaryBonusDto> temporaryBonuses,
        List<BleedingDto> bleedingEffects,
        List<ManaDrainDto> manaDrains,
        List<WitheringDto> witheringEffects,
        List<PendingEgoRecoveryDto> pendingEgoRecoveries,
        List<LifeStealDto> lifeSteals,
        List<InventoryItemDto> inventory,
        String tokenImageUrl,
        String campaignId,
        boolean progressionLocked
) {
}
