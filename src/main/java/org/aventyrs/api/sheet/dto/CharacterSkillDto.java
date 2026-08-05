package org.aventyrs.api.sheet.dto;

/** {@code graduationValue} is nullable, defaulting to 0 (core's own {@code SkillGraduation} default) when omitted. */
public record CharacterSkillDto(String specialization, Integer graduationValue) {
}
