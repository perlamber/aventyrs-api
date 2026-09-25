package org.aventyrs.api.campaign.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * A Saquear, or the end-of-combat sweep: everything the foe carries moves into the bag. {@code
 * sourceSheetId} is either a monster sheet or a character sheet, the same way a Cena participant
 * id resolves.
 */
public record BagLootRequest(@NotBlank String sourceSheetId) {
}
