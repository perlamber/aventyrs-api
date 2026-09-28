package org.aventyrs.api.sheet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.aventyrs.core.skill.SkillTraitKind;
import org.aventyrs.core.skill.SkillType;

/** See {@code BackgroundTraitEntry}. */
public record BackgroundTraitDto(@NotNull SkillType skill, @NotNull SkillTraitKind kind, @NotBlank String name) {
}
