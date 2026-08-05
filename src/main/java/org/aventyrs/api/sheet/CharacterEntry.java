package org.aventyrs.api.sheet;

import java.util.Map;
import org.aventyrs.core.character.AttributeDomain;
import org.aventyrs.core.character.Character.Sexo;
import org.aventyrs.core.character.SizeCategory;
import org.aventyrs.core.skill.SkillType;

/**
 * Persisted mirror of a core {@code Character}'s identity and build fields, nested directly
 * inside {@code CharacterSheetDocument} rather than referenced by id: Mongo's document model
 * makes denormalizing this natural, and nothing in this API modifies a Character independently
 * of the CharacterSheet wrapping it. {@code race} is a plain identifier (e.g. {@code "HUMAN"})
 * since core's {@code Race} is a stateless interface with one implementation ({@code Human}) so
 * far, not an enum this layer can reference directly. {@code skills} only contains entries for
 * Perícias actually trained — an absent {@link SkillType} key means untrained, same convention
 * as core's own {@code Character#skills}, unlike {@code attributes}, which always has all seven
 * {@link AttributeDomain} keys (core defaults every attribute to base 1 whether or not a caller
 * ever mentions it).
 *
 * <p>The id field is named {@code characterId}, not {@code id}: Spring Data MongoDB's
 * {@code MappingMongoConverter} treats a property literally named {@code id} as that type's
 * identifier in every mapped type it walks, not just {@code @Document} roots — it would read
 * this field from a {@code _id} key inside the {@code character} subdocument, which doesn't
 * exist, and always deserialize it as {@code null}.
 */
public record CharacterEntry(
        String characterId,
        String name,
        String race,
        Sexo sexo,
        int tendencia,
        SizeCategory sizeCategory,
        Map<AttributeDomain, AttributeValueEntry> attributes,
        Map<SkillType, CharacterSkillEntry> skills
) {
}
