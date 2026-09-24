package org.aventyrs.api.campaign.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.aventyrs.api.item.dto.InventoryItemDto;

/** The GM puts an item into the bag. {@code sourceName} is optional, for the bag to show where it came from. */
public record BagAddRequest(
        @NotNull @Valid InventoryItemDto item,
        String sourceName) {
}
