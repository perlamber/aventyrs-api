package org.aventyrs.api.sheet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.aventyrs.core.skill.SkillType;

import java.util.List;

/** See {@code BackgroundEntry}. Nullable lists read as empty. */
public record BackgroundDto(@NotBlank String type, List<SkillType> graduationSkills,
                            List<@Valid BackgroundTraitDto> traits, List<String> benefitChoices) {
}
