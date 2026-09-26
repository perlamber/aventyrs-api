package org.aventyrs.api.monster.dto;

import org.aventyrs.api.item.dto.InventoryItemDto;
import org.aventyrs.core.monster.MonsterCategory;
import org.aventyrs.core.monster.MonsterViolation;
import org.aventyrs.core.monster.SkillDifficulty;
import org.aventyrs.core.skill.SkillType;
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

/**
 * A monster as stored — its {@code blueprint} — plus everything core derives from it, computed
 * fresh on every read: the Categoria, the Defesas, a GD for <i>every</i> Perícia, the PV/PD/PM and
 * PA maxima (at rest — nothing on the sheet's play state is folded in), and any {@code violations}
 * the blueprint has (empty for anything written since 0.0.59, which refuses an illegal one; a later
 * rules change could make an old blueprint report some).
 *
 * <p>{@code generalDifficulty} is what an untrained Perícia presents; {@code skillDifficulties}
 * already covers every Perícia, so a reader never needs to fall back to it.
 */
public record MonsterSheetResponse(
        String id,
        MonsterBlueprintDto blueprint,
        CharacterResponse character,
        String playerId,
        MonsterCategory category,
        int physicalDefense,
        int magicDefense,
        SkillDifficulty generalDifficulty,
        Map<SkillType, SkillDifficulty> skillDifficulties,
        int maxHitPoints,
        int maxDeterminationPoints,
        int maxMagicPoints,
        int maxActionPoints,
        boolean undead,
        Set<CriticalEffectType> criticalEffectImmunities,
        List<MonsterViolation> violations,
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
        List<InventoryItemDto> inventory,
        String tokenImageUrl
) {
}
