package org.aventyrs.api.item;

import org.aventyrs.core.character.DefenseType;
import org.aventyrs.core.item.DefensiveMasterpiece;
import org.aventyrs.core.modifier.ModifierType;

/**
 * Persisted mirror of a core {@code ItemMasterpiece} — the Obra-Prima fitted to one item copy.
 * {@code AbstractItem#setMasterpiece} only ever accepts an {@code ItemMasterpiece} wrapping a
 * {@link DefensiveMasterpiece} definition (a bare {@code DefensiveMasterpiece} is rejected), so
 * this is the only shape a forged item's masterpiece takes. {@code selectedDefense} is set only
 * for {@code DefensiveMasterpiece#MAGISTRAL}, {@code selectedActionBonus} only for {@code
 * DefensiveMasterpiece#SOB_MEDIDA} — {@code null} otherwise.
 */
public record ItemMasterpieceEntry(
        DefensiveMasterpiece definition,
        DefenseType selectedDefense,
        ModifierType selectedActionBonus
) {
}
