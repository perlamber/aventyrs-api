package org.aventyrs.api.monster.dto;

import java.util.Map;
import org.aventyrs.core.monster.SkillDifficulty;
import org.aventyrs.core.skill.SkillType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import org.aventyrs.api.sheet.dto.CharacterDto;
import org.aventyrs.core.effect.CriticalEffectType;

/**
 * Mirrors core's {@code MonsterSheet.of(Character, Player, MonsterTemplate)}: a new sheet needs
 * its character and owning GM plus the stat-block combat numbers and its anatomy
 * (undead/immunities) — every resource/temporary-effect field starts at zero/empty, same as the
 * domain factory. {@code generalDifficulty} defaults to Médio +0 ({@code SkillDifficulty.DEFAULT}) and {@code
 * skillDifficulties} to empty, {@code
 * undead} to {@code false} and {@code criticalEffectImmunities} to empty when omitted, matching
 * core's {@code AbstractMonsterTemplate}'s own {@code @Builder.Default}s.
 */
public record MonsterSheetCreateRequest(
        @NotNull @Valid CharacterDto character,
        @NotBlank String playerId,
        int physicalDefense,
        int magicDefense,
        SkillDifficulty generalDifficulty,
        Map<SkillType, SkillDifficulty> skillDifficulties,
        Boolean undead,
        Set<CriticalEffectType> criticalEffectImmunities,
        String tokenImageUrl
) {
}
