package org.aventyrs.api.sheet;

import java.util.List;
import org.aventyrs.core.character.AttributeDomain;

/**
 * Persisted mirror of a core {@code Race}: a plain identifier (e.g. {@code "HUMAN"}, {@code
 * "AGASTIAS"}, {@code "MEIO_ELFO"}) plus the extra state only the 8 stateful implementations
 * carry — the 7 {@code AbstractMesticoRace} subclasses and {@code MeioElfo} — since {@code Race}
 * has 20 implementations and no discriminator enum of its own to reference directly, the same
 * reasoning {@link CharacterSkillEntry} already uses for {@code SkillCompetencyAbility}'s 14
 * implementations.
 *
 * <p>{@code parentRaceType} holds the same kind of identifier as {@code type}; null for the 12
 * stateless races. Core's own {@code AbstractMesticoRace} and {@code MeioElfo} constructors both
 * reject a Mestiço/MeioElfo parent, so a parent is always one of the 12 stateless races — never
 * itself needing the extra fields, hence a plain identifier here rather than a nested {@code
 * RaceEntry}.
 *
 * <p>{@code linhagem} holds core's {@code Agastias.Linhagem} enum constant name ({@code
 * "VULCANO"}/{@code "TROVEJANTE"}); null for every other type. {@code chosenInheritedAttribute}
 * is core's {@code MeioElfo#getChosenInheritedAttribute()} — unlike the other extra fields it's a
 * real, single core enum ({@link AttributeDomain}, already used directly elsewhere in this
 * record's sibling {@code CharacterEntry#attributes}), so it's stored as-is; null except for
 * {@code MEIO_ELFO}, and even there optional (core's 1-arg {@code MeioElfo(Race)} constructor
 * omits it).
 *
 * <p>{@code inheritedRacialAbilities} holds {@code SkillCompetencyAbility} enum constant names
 * granted by the parent race — populated for the 7 {@code AbstractMesticoRace} subclasses and
 * {@code MeioElfo}, empty for the 12 stateless races. {@code inheritedAttributeAbilities} holds
 * {@code AttributeAbility} enum constant names the same way, but only {@code AbstractMesticoRace}
 * subclasses have this concept — {@code MeioElfo} does not, so it's always empty there. Both are
 * empty lists rather than null when not applicable, same convention as {@link
 * CharacterSkillEntry}'s lists.
 */
public record RaceEntry(
        String type,
        String parentRaceType,
        String linhagem,
        AttributeDomain chosenInheritedAttribute,
        List<String> inheritedRacialAbilities,
        List<String> inheritedAttributeAbilities
) {
}
