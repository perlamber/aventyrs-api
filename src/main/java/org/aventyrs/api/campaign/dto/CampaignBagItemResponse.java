package org.aventyrs.api.campaign.dto;

import java.time.Instant;
import org.aventyrs.api.item.dto.InventoryItemDto;

/** One item in a Campanha's bag. {@code id} is what a claim names; {@code sourceName} is {@code null} for GM-added loot. */
public record CampaignBagItemResponse(
        String id,
        InventoryItemDto item,
        String sourceName,
        Instant addedAt) {
}
