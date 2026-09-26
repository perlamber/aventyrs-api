package org.aventyrs.api.monster.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aventyrs.api.sheet.dto.FeatDto;
import org.aventyrs.core.character.AttributeDomain;
import org.aventyrs.core.character.EgoDomain;
import org.aventyrs.core.character.SizeCategory;
import org.aventyrs.core.effect.CriticalEffectType;
import org.aventyrs.core.monster.MonsterKind;
import org.aventyrs.core.monster.model.MonsterModel;
import org.aventyrs.core.skill.SkillType;

/**
 * A monster as the Mestre built it — the wire form of core's {@code MonsterBlueprint}: the
 * <i>choices</i> only. Every number (GDs, Defesas, PA, PV/PD/PM) is derived from it by core's
 * {@code MonsterRules} and returned beside it on {@link MonsterSheetResponse}; none is accepted
 * here.
 *
 * <p>Only {@code name} is required. A missing {@code kind} is Regular, {@code sizeCategory} is
 * ZERO, an Atributo left out of {@code attributeBases} is base 1, and every other collection
 * reads as empty.
 *
 * <p>Not carried yet: equipment (a monster's worn items — its loot is the sheet's {@code
 * inventory}) and an Exemplar's Habilidades de Atributo, Habilidades de Competência,
 * Especializações and Vantagens de Ego. Core models all of them; the editor offers none, and the
 * Mestre's {@code adjustments} cover a Defesa or GD they would have moved.
 */
public record MonsterBlueprintDto(
        @NotBlank String name,
        int powerDegree,
        MonsterKind kind,
        SizeCategory sizeCategory,
        Map<AttributeDomain, Integer> attributeBases,
        Set<SkillType> trainedSkills,
        Set<SkillType> gnoseUpgrades,
        Map<SkillType, Integer> progressionUpgrades,
        List<MonsterModel> models,
        List<@Valid MonstrousAbilityDto> abilities,
        List<@Valid FeatDto> feats,
        Map<EgoDomain, Integer> egoAllocation,
        @Valid MonsterAdjustmentsDto adjustments,
        Boolean undead,
        Set<CriticalEffectType> criticalEffectImmunities,
        int famaPositiva,
        int famaNegativa
) {
}
