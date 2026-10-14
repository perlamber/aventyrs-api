package org.aventyrs.api.item;

import org.aventyrs.core.character.DefenseType;
import org.aventyrs.core.magic.ElementalType;
import org.aventyrs.core.scene.TerrainType;

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
 * DefensiveImprovement#BENCAO_ELEMENTAL} or (core 0.1.6.9) {@code OffensiveImprovement#BENCAO_ELEMENTAL}
 * — {@code null} otherwise. No migration: an older offensive Benção Elemental simply has no element.
 *
 * <p>{@code selectedTerrain} is set only for {@code DefensiveImprovement#CAMUFLADA} (core 0.1.6.11) — the
 * environment its extra Furtividade Vantagem holds in. No migration: an older Camuflada has none, and keeps only
 * its plain Vantagem.
 */
public record ItemImprovementEntry(
        String definition,
        DefenseType selectedDefense,
        ElementalType selectedElementalType,
        TerrainType selectedTerrain
) {

    /** An Aprimoramento with no terrain — every one before core 0.1.6.11. */
    public ItemImprovementEntry(String definition, DefenseType selectedDefense, ElementalType selectedElementalType) {
        this(definition, selectedDefense, selectedElementalType, null);
    }
}
