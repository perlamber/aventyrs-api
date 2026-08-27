package org.aventyrs.api.monster.dto;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aventyrs.api.sheet.dto.BleedingDto;
import org.aventyrs.api.sheet.dto.CharacterResponse;
import org.aventyrs.api.sheet.dto.LifeStealDto;
import org.aventyrs.api.sheet.dto.ManaDrainDto;
import org.aventyrs.api.sheet.dto.PendingEgoRecoveryDto;
import org.aventyrs.api.sheet.dto.TemporaryBonusDto;
import org.aventyrs.api.sheet.dto.WitheringDto;
import org.aventyrs.core.character.EgoDomain;
import org.aventyrs.core.effect.CriticalEffectType;
import org.aventyrs.core.skill.DifficultyLevel;

public record MonsterSheetResponse(
        String id,
        CharacterResponse character,
        String playerId,
        int physicalDefense,
        int magicDefense,
        DifficultyLevel attackDifficulty,
        int attackBonus,
        boolean undead,
        Set<CriticalEffectType> criticalEffectImmunities,
        int damageTaken,
        int manaSpent,
        int determinationSpent,
        int shieldPoints,
        Map<EgoDomain, Integer> temporaryEgoPoints,
        List<TemporaryBonusDto> temporaryBonuses,
        List<BleedingDto> bleedingEffects,
        List<ManaDrainDto> manaDrains,
        List<WitheringDto> witheringEffects,
        List<PendingEgoRecoveryDto> pendingEgoRecoveries,
        List<LifeStealDto> lifeSteals,
        List<String> inventory,
        String tokenImageUrl
) {
}
