package org.aventyrs.api.sheet.dto;

import org.aventyrs.core.skill.SkillType;

import java.util.List;

/** See {@code BackgroundEntry}; {@code kind} ({@code ORIGIN}/{@code CAREER}) is resolved from core for the reader's convenience. */
public record BackgroundResponse(String type, String kind, List<SkillType> graduationSkills,
                                 List<BackgroundTraitDto> traits, List<String> benefitChoices) {
}
