package org.aventyrs.api.monster.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import org.aventyrs.api.sheet.dto.CharacterDto;
import org.aventyrs.core.effect.CriticalEffectType;
import org.aventyrs.core.skill.DifficultyLevel;

/**
 * Mirrors core's {@code MonsterSheet.of(Character, Player, MonsterTemplate)}: a new sheet needs
 * its character and owning GM plus the four stat-block combat numbers and its anatomy
 * (undead/immunities) — every resource/temporary-effect field starts at zero/empty, same as the
 * domain factory. {@code attackDifficulty} defaults to {@code DifficultyLevel.MEDIUM}, {@code
 * undead} to {@code false} and {@code criticalEffectImmunities} to empty when omitted, matching
 * core's {@code AbstractMonsterTemplate}'s own {@code @Builder.Default}s.
 */
public record MonsterSheetCreateRequest(
        @NotNull @Valid CharacterDto character,
        @NotBlank String playerId,
        int physicalDefense,
        int magicDefense,
        DifficultyLevel attackDifficulty,
        int attackBonus,
        Boolean undead,
        Set<CriticalEffectType> criticalEffectImmunities,
        String tokenImageUrl
) {
}
