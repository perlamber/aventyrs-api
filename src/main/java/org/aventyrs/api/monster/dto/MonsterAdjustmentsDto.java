package org.aventyrs.api.monster.dto;

import java.util.Map;
import org.aventyrs.core.skill.SkillType;

/**
 * The Mestre's signed deltas on the numbers core derives — core's {@code MonsterAdjustments}.
 * Never validated. A {@code null} map is empty.
 */
public record MonsterAdjustmentsDto(
        Map<SkillType, Integer> skillLevelShifts,
        Map<SkillType, Integer> skillBonuses,
        int physicalDefense,
        int magicDefense,
        int actionPoints,
        int lifeMultiplier,
        int manaMultiplier,
        int determinationMultiplier
) {
}
