package org.aventyrs.api.campaign.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * A participant puts a carried item into the bag. An inventory item has no id, so it is named by
 * its index, and {@code itemName} must match what is at that index. A stale index from a sheet
 * that changed since the client last read it is refused instead of moving the wrong item.
 */
public record BagDepositRequest(
        @NotBlank String characterSheetId,
        @PositiveOrZero int inventoryIndex,
        @NotBlank String itemName) {
}
