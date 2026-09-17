package org.aventyrs.api.item.dto;

import jakarta.validation.constraints.NotBlank;
import org.aventyrs.core.character.DefenseType;
import org.aventyrs.core.magic.ElementalType;

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
 * DefensiveImprovement#BENCAO_ELEMENTAL}. An offensive Aprimoramento records no choice at all —
 * core deliberately drops {@code OffensiveImprovement#BENCAO_ELEMENTAL}'s element, having nothing
 * that reads it — so both are always {@code null} on a weapon's Aprimoramento.
 */
public record ItemImprovementDto(
        @NotBlank String definition,
        DefenseType selectedDefense,
        ElementalType selectedElementalType
) {
}
