package org.aventyrs.api.sheet;

import org.aventyrs.core.skill.SkillTraitKind;
import org.aventyrs.core.skill.SkillType;

/**
 * One Especialização or Habilidade de Competência an Antecedente granted. A bare constant name is
 * ambiguous across Perícias and kinds ({@code ACROBATA} is both an Atletismo Especialização and
 * Competência; {@code ARMAS_TECNOLOGICAS} exists for both attack Perícias), hence all three parts.
 */
public record BackgroundTraitEntry(SkillType skill, SkillTraitKind kind, String name) {
}
