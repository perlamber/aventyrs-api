package org.aventyrs.api.item.dto;

import jakarta.validation.constraints.NotBlank;
import org.aventyrs.core.character.DefenseType;
import org.aventyrs.core.magic.ElementalType;
import org.aventyrs.core.scene.TerrainType;

/**
 * See {@code ItemImprovementEntry} for the persisted shape this mirrors.
 *
 * <p>{@code definition} is a catalog constant's own {@code name()}, resolved against the
 * Defensivos or the Ofensivos catalog according to the host item's {@code category} — see {@link
 * ItemMasterpieceDto}, which carries the same convention for the same reason (the two
 * Aprimoramento tables share {@code ENCAIXE} and {@code BENCAO_ELEMENTAL}, among others).
 *
 * <p>{@code selectedDefense} is set only for {@code DefensiveImprovement#CAMADA_DE_REFORCO} on a
 * non-shield host, {@code selectedElementalType} only for {@code
 * DefensiveImprovement#BENCAO_ELEMENTAL} and, since core 0.1.6.9, {@code
 * OffensiveImprovement#BENCAO_ELEMENTAL} — the element a weapon's hits now deal on top of their own
 * type. {@code selectedDefense} is always {@code null} on a weapon's Aprimoramento.
 *
 * <p>{@code selectedTerrain} is set only for {@code DefensiveImprovement#CAMUFLADA} (core 0.1.6.11).
 */
public record ItemImprovementDto(
        @NotBlank String definition,
        DefenseType selectedDefense,
        ElementalType selectedElementalType,
        TerrainType selectedTerrain
) {

    /** An Aprimoramento with no terrain — every one before core 0.1.6.11. */
    public ItemImprovementDto(String definition, DefenseType selectedDefense, ElementalType selectedElementalType) {
        this(definition, selectedDefense, selectedElementalType, null);
    }
}
