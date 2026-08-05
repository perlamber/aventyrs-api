package org.aventyrs.api.sheet;

import org.aventyrs.core.character.Character.Sexo;

/**
 * Persisted mirror of a core {@code Character}'s identity fields, nested directly inside
 * {@code CharacterSheetDocument} rather than referenced by id: Mongo's document model makes
 * denormalizing this natural, and nothing in this API modifies a Character independently of the
 * CharacterSheet wrapping it. {@code race} is a plain identifier (e.g. {@code "HUMAN"}) since
 * core's {@code Race} is a stateless interface with one implementation ({@code Human}) so far,
 * not an enum this layer can reference directly.
 *
 * <p>The id field is named {@code characterId}, not {@code id}: Spring Data MongoDB's
 * {@code MappingMongoConverter} treats a property literally named {@code id} as that type's
 * identifier in every mapped type it walks, not just {@code @Document} roots — it would read
 * this field from a {@code _id} key inside the {@code character} subdocument, which doesn't
 * exist, and always deserialize it as {@code null}.
 */
public record CharacterEntry(String characterId, String name, String race, Sexo sexo, int tendencia) {
}
