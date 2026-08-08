package org.aventyrs.api.sheet.dto;

import java.util.List;

/**
 * {@code specializations} holds the names of the chosen {@code SkillSpecialization} enum
 * constants for this Perícia (e.g. {@code "PINTURA"}); nullable/omittable the same way {@code
 * CharacterSheetService#normalizeSkills} treats an absent skill entry — an untrained or
 * not-yet-specialized Perícia simply has none. {@code graduationValue} is nullable, defaulting
 * to 0 (core's own {@code SkillGraduation} default) when omitted.
 */
public record CharacterSkillDto(List<String> specializations, Integer graduationValue) {
}
