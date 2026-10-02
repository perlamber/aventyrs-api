package org.aventyrs.api.monster;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aventyrs.api.sheet.FeatEntry;
import org.aventyrs.core.character.AttributeDomain;
import org.aventyrs.core.character.EgoDomain;
import org.aventyrs.core.character.SizeCategory;
import org.aventyrs.core.effect.CriticalEffectType;
import org.aventyrs.core.monster.MonsterKind;
import org.aventyrs.core.monster.model.MonsterModel;
import org.aventyrs.core.skill.SkillType;

/**
 * Persisted mirror of {@link org.aventyrs.api.monster.dto.MonsterBlueprintDto} — the Mestre's
 * choices, normalized: no {@code null} collection, a {@code kind} and {@code sizeCategory} always
 * set. Everything derived is recomputed by core from this on every read.
 */
public record MonsterBlueprintEntry(
        String name,
        int powerDegree,
        MonsterKind kind,
        SizeCategory sizeCategory,
        Map<AttributeDomain, Integer> attributeBases,
        Set<SkillType> trainedSkills,
        Set<SkillType> gnoseUpgrades,
        Map<SkillType, Integer> progressionUpgrades,
        List<MonsterModel> models,
        List<MonstrousAbilityEntry> abilities,
        List<FeatEntry> feats,
        Map<EgoDomain, Integer> egoAllocation,
        MonsterAdjustmentsEntry adjustments,
        boolean undead,
        boolean intelligent,
        Set<CriticalEffectType> criticalEffectImmunities,
        int famaPositiva,
        int famaNegativa
) {

    /**
     * A held Habilidade Monstruosa — see {@link org.aventyrs.api.monster.dto.MonstrousAbilityDto}.
     * {@code choice} is only ever read (documents written before core 0.0.60); new writes carry
     * {@code choices}.
     */
    public record MonstrousAbilityEntry(MonsterModel model, String ability, String choice,
                                        Map<String, List<String>> choices) {
    }

    /** The Mestre's deltas — see {@link org.aventyrs.api.monster.dto.MonsterAdjustmentsDto}. */
    public record MonsterAdjustmentsEntry(
            Map<SkillType, Integer> skillLevelShifts,
            Map<SkillType, Integer> skillBonuses,
            int physicalDefense,
            int magicDefense,
            int actionPoints,
            int lifeMultiplier,
            int manaMultiplier,
            int determinationMultiplier) {
    }
}
