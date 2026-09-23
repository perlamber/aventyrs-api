package org.aventyrs.api.item.dto;

import jakarta.validation.constraints.NotBlank;
import org.aventyrs.core.character.DefenseType;
import org.aventyrs.core.modifier.ModifierType;

/**
 * See {@code ItemMasterpieceEntry} for the persisted shape this mirrors.
 *
 * <p>{@code definition} is a catalog constant's own {@code name()} rather than a typed enum,
 * because since core 0.0.35 there are two Obra-Prima catalogs and <b>the name alone is not a
 * key</b>: {@code MAGISTRAL}, {@code CONJURADORA}, {@code EQUILIBRADA}, {@code BANHADA_EM_PRATA}
 * and {@code DYOSPIROS} are all printed in both the Defensivas and the Ofensivas tables with
 * different columns. Which catalog a name resolves against is decided by the host item's own
 * {@code category} — {@code ItemCategory#getType()} — which every carrier of this record ({@link
 * InventoryItemDto}, {@link ItemRequest}, {@link ItemResponse}) already states. Storing the
 * discriminator again here would be a second source of truth for the same fact.
 *
 * <p>This API neither resolves nor validates that name: it stores and returns the shape, exactly
 * as it does for {@code InventoryItemDto#templateName}. The client forges the copy.
 *
 * <p>{@code selectedDefense} is set only for {@code DefensiveMasterpiece#MAGISTRAL}, {@code
 * selectedActionBonus} only for {@code DefensiveMasterpiece#SOB_MEDIDA}. No offensive entry
 * carries a creation-time choice, so both are always {@code null} on a weapon's Obra-Prima.
 */
public record ItemMasterpieceDto(
        @NotBlank String definition,
        DefenseType selectedDefense,
        ModifierType selectedActionBonus
) {
}
