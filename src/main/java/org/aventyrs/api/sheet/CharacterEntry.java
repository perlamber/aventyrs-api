package org.aventyrs.api.sheet;

import java.util.List;
import java.util.Map;
import org.aventyrs.core.action.ActionProfile;
import org.aventyrs.core.character.AttributeDomain;
import org.aventyrs.core.character.Character.Sexo;
import org.aventyrs.core.character.CharacterStatus;
import org.aventyrs.core.character.Deity;
import org.aventyrs.core.character.EgoDomain;
import org.aventyrs.core.character.SizeCategory;
import org.aventyrs.core.skill.SkillType;

/**
 * Persisted mirror of a core {@code Character}'s identity and build fields, nested directly
 * inside {@code CharacterSheetDocument} rather than referenced by id: Mongo's document model
 * makes denormalizing this natural, and nothing in this API modifies a Character independently
 * of the CharacterSheet wrapping it. See {@link RaceEntry} for how {@code race} mirrors core's
 * polymorphic {@code Race} interface. {@code skills} only contains entries for
 * Perícias actually trained — an absent {@link SkillType} key means untrained, same convention
 * as core's own {@code Character#skills}, unlike {@code attributes}/{@code egos}, which always
 * have every {@link AttributeDomain}/{@link EgoDomain} key (core defaults every attribute to
 * base 1, every Ego to base 2, whether or not a caller ever mentions it).
 *
 * <p>The id field is named {@code characterId}, not {@code id}: Spring Data MongoDB's
 * {@code MappingMongoConverter} treats a property literally named {@code id} as that type's
 * identifier in every mapped type it walks, not just {@code @Document} roots — it would read
 * this field from a {@code _id} key inside the {@code character} subdocument, which doesn't
 * exist, and always deserialize it as {@code null}.
 *
 * <p>{@code actionProfile} is nullable here (unlike {@code CharacterDto}'s required field) only
 * so documents persisted before this field existed still deserialize; every document written
 * through {@code CharacterSheetService} from now on always has one.
 *
 * <p>{@code attributeAbilities}/{@code activeAbilities} store core's {@code
 * Character#attributeAbilities}/{@code Character#activeAbilities} as their constants' own {@code
 * name()}s (or, for {@code activeAbilities}' one non-enum implementer so far, its class's simple
 * name), the same "names rather than polymorphic core types" shape {@link
 * RaceEntry#inheritedAttributeAbilities} and {@link CharacterSkillEntry} already use — core's
 * {@code AttributeAbility}/{@code ActiveAbility} are interfaces (eight and one implementers,
 * respectively), which no single Mongo type mapping could round-trip. {@code egoAdvantages} does
 * the same per {@link EgoDomain} for core's {@code Character#egoAdvantages} — a domain with no
 * eligible or chosen Vantagem is simply absent, same convention as {@code skills}. Documents
 * written before any of these fields existed deserialize them as {@code null}, which {@code
 * CharacterSheetService} normalizes to an empty list/map.
 *
 * <p>{@code Character#skillCompetencyAbilities} isn't mirrored as its own top-level field here —
 * every {@code SkillCompetencyAbility} is already scoped to one {@link SkillType}, so it's
 * carried per-skill instead, via {@link CharacterSkillEntry#competencyAbilities}. {@code
 * Character#abilityChoices} (the open-ended {@code AcquiredChoice} mechanism) has no persisted
 * mirror yet — no ability in core currently uses that pattern for real, so there's nothing
 * concrete to shape a persisted form around; see core's own CLAUDE.md ("Acquisition-time ability
 * choices").
 *
 * <p>{@code status}, {@code actionPoints}, {@code temporaryActionPointsBonus}, {@code
 * reactions}, {@code freeActions}, {@code manaMultiplier}, {@code lifeMultiplier}, {@code
 * determinationMultiplier}, and {@code centelhaSuperiorSelected} are all nullable, unlike {@code
 * tendencia} — this schema already has real documents predating these fields, so (same reasoning
 * as {@code sizeCategory}) they need a null-safe fallback at read time, not just a
 * default-when-omitted at write time: {@code status} to {@code CharacterStatus.CLEAN}; {@code
 * actionPoints}/{@code reactions}/{@code freeActions}/{@code manaMultiplier}/{@code
 * lifeMultiplier}/{@code determinationMultiplier} to their respective {@code
 * <Stat>Service.DEFAULT_*} constants; {@code temporaryActionPointsBonus} to 0; {@code
 * centelhaSuperiorSelected} to {@code false}. Every document written through {@code
 * CharacterSheetService} from now on always has a resolved, non-null value for each.
 *
 * <p>{@code feats}/{@code equipment} store core's {@code Character#feats}/{@code
 * Character#equipment} the same "constant's own {@code name()}" way {@code attributeAbilities}/
 * {@code activeAbilities} already do — {@code Feat} and {@code Item} are both interfaces backed
 * by catalog enums ({@code ArtesMarciaisFeat}, {@code ArmorItem}). Empty, not {@code null}, when
 * nothing is held/equipped.
 *
 * <p>{@code primaryTitle}/{@code secondaryTitle}/{@code tertiaryTitle} mirror core's three
 * Título slots; {@code null} for an empty slot. See {@link TitleEntry} for how a held Título is
 * shaped.
 */
public record CharacterEntry(
        String characterId,
        String name,
        RaceEntry race,
        Sexo sexo,
        Deity deity,
        int tendencia,
        SizeCategory sizeCategory,
        ActionProfile actionProfile,
        Map<AttributeDomain, AttributeValueEntry> attributes,
        Map<EgoDomain, EgoValueEntry> egos,
        Map<SkillType, CharacterSkillEntry> skills,
        List<String> attributeAbilities,
        Map<EgoDomain, String> egoAdvantages,
        List<String> activeAbilities,
        Integer actionPoints,
        Integer temporaryActionPointsBonus,
        CharacterStatus status,
        Integer reactions,
        Integer freeActions,
        Integer manaMultiplier,
        Integer lifeMultiplier,
        Integer determinationMultiplier,
        Boolean centelhaSuperiorSelected,
        List<String> feats,
        List<String> equipment,
        TitleEntry primaryTitle,
        TitleEntry secondaryTitle,
        TitleEntry tertiaryTitle
) {
}
