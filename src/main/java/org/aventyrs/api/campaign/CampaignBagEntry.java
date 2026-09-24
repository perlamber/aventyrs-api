package org.aventyrs.api.campaign;

import java.time.Instant;
import org.aventyrs.api.item.InventoryItemEntry;

/**
 * One item in a Campanha's shared bag. {@code id} is this entry's own, which a claim names. The
 * item has no identity of its own ({@link InventoryItemEntry} is embedded wherever it lives).
 * {@code sourceName} is the looted foe's name, or {@code null} when the GM added the item.
 */
public record CampaignBagEntry(
        String id,
        InventoryItemEntry item,
        String sourceName,
        Instant addedAt) {
}
