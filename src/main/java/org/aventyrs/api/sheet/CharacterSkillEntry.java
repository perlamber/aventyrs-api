package org.aventyrs.api.sheet;

import java.util.List;

/**
 * Persisted mirror of a trained {@code CharacterSkill}: which concrete {@code Skill} it is
 * comes from the enclosing {@code Map}'s {@code SkillType} key (same reasoning core itself
 * uses), so only {@code specializations}, {@code competencyAbilities}, and the current
 * Graduação value need to live here. {@code specializations} holds the names of the held
 * {@code SkillSpecialization} enum constants (e.g. {@code "PINTURA"} for core's {@code
 * ArtesSpecialization#PINTURA}) — core's own {@code CharacterSkill#specializations} is a
 * {@code List<SkillSpecialization>}, but that interface has 14 different enum implementations,
 * one per {@link org.aventyrs.core.skill.SkillType}, so this persisted mirror stores names
 * instead of trying to round-trip a polymorphic core type through Mongo/Jackson. {@code
 * competencyAbilities} does the same for the held {@code SkillCompetencyAbility} enum
 * constants (e.g. {@code "DOM_BARDICO"} for core's {@code ArtesCompetencyAbility#DOM_BARDICO}).
 */
public record CharacterSkillEntry(List<String> specializations, List<String> competencyAbilities, int graduationValue) {
}
