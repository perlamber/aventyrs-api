package org.aventyrs.api.sheet;

import java.util.Map;
import org.aventyrs.core.action.ActionProfile;
import org.aventyrs.core.character.AttributeDomain;
import org.aventyrs.core.character.Character.Sexo;
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
 */
public record CharacterEntry(
        String characterId,
        String name,
        RaceEntry race,
        Sexo sexo,
        int tendencia,
        SizeCategory sizeCategory,
        ActionProfile actionProfile,
        Map<AttributeDomain, AttributeValueEntry> attributes,
        Map<EgoDomain, EgoValueEntry> egos,
        Map<SkillType, CharacterSkillEntry> skills
) {
}
