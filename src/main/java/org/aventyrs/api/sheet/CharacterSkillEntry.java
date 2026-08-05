package org.aventyrs.api.sheet;

/**
 * Persisted mirror of a trained {@code CharacterSkill}: which concrete {@code Skill} it is
 * comes from the enclosing {@code Map}'s {@code SkillType} key (same reasoning core itself
 * uses), so only {@code specialization} and the current Graduação value need to live here.
 */
public record CharacterSkillEntry(String specialization, int graduationValue) {
}
