package org.aventyrs.api.sheet;

import org.aventyrs.core.skill.SkillType;

import java.util.List;

/**
 * Persisted mirror of core's {@code AcquiredBackground} — one Antecedente a character holds, with the
 * picks recorded for it. {@code type} is the {@code Background#name()} ({@code "OFI"}, {@code
 * "BATEDOR"}); {@code graduationSkills}/{@code traits} are what core's {@code
 * CharacterCreationService#applyBackground} stored — every Perícia and trait the Antecedente gave,
 * fixed ones included — so they are provenance, not grants: the Graduações and traits themselves are
 * already in {@code skills}, and nothing re-applies them. {@code benefitChoices} are the Benefício's
 * picks as names ({@code SkillType}/{@code MagicTree} constants), routed by the choice's own type.
 */
public record BackgroundEntry(String type, List<SkillType> graduationSkills, List<BackgroundTraitEntry> traits,
                              List<String> benefitChoices) {
}
