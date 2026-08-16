package org.aventyrs.api.sheet.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.aventyrs.core.character.EgoDomain;

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
        Map<EgoDomain, Integer> temporaryEgoPoints,
        List<TemporaryBonusDto> temporaryBonuses,
        List<BleedingDto> bleedingEffects,
        List<ManaDrainDto> manaDrains,
        List<WitheringDto> witheringEffects,
        List<PendingEgoRecoveryDto> pendingEgoRecoveries
) {
}
