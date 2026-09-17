package org.aventyrs.api.item;

import org.aventyrs.core.character.DefenseType;
import org.aventyrs.core.magic.ElementalType;

/**
 * Persisted mirror of one Aprimoramento fitted to one item copy.
 *
 * <p>{@code definition} is the catalog constant's own {@code name()}, read against the Defensivos
 * or the Ofensivos catalog according to the host item's {@code category} — see {@link
 * ItemMasterpieceEntry} for why the name stopped being a key on its own in core 0.0.35, and why
 * widening it here migrates nothing.
 *
 * <p>{@code selectedDefense} is set only for {@code DefensiveImprovement#CAMADA_DE_REFORCO} on a
 * non-shield host, {@code selectedElementalType} only for {@code
 * DefensiveImprovement#BENCAO_ELEMENTAL} — {@code null} otherwise, and always {@code null} for an
 * offensive entry.
 */
public record ItemImprovementEntry(
        String definition,
        DefenseType selectedDefense,
        ElementalType selectedElementalType
) {
}
