package org.aventyrs.api.item;

import org.aventyrs.core.character.DefenseType;
import org.aventyrs.core.modifier.ModifierType;

/**
 * Persisted mirror of the Obra-Prima fitted to one item copy.
 *
 * <p>{@code definition} is the catalog constant's own {@code name()}. It was a typed {@code
 * DefensiveMasterpiece} while that was the only authored catalog; core 0.0.35 added {@code
 * OffensiveMasterpiece}, whose constants deliberately reuse several of the same names for
 * differently-columned entries, so the name is no longer a key on its own — the host item's
 * {@code category} says which catalog to read it against. Stored documents are unaffected: an
 * enum already persisted as its {@code name()}, so this widens the field's Java type without
 * changing a byte in Mongo.
 *
 * <p>{@code selectedDefense} is set only for {@code DefensiveMasterpiece#MAGISTRAL}, {@code
 * selectedActionBonus} only for {@code DefensiveMasterpiece#SOB_MEDIDA} — {@code null}
 * otherwise, and always {@code null} for an offensive entry, none of which carries a
 * creation-time choice.
 */
public record ItemMasterpieceEntry(
        String definition,
        DefenseType selectedDefense,
        ModifierType selectedActionBonus
) {
}
