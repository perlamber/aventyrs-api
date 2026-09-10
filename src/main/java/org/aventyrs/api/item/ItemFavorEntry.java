package org.aventyrs.api.item;

import java.util.List;
import org.aventyrs.core.character.AttributeDomain;

/**
 * Persisted mirror of a core {@code ItemFavor} — the item's real, conditional benefit.
 * {@code requiredAttributeDomain} is {@code null} for a Favor granted unconditionally, the same
 * convention core's own {@code ItemRequirements} follows.
 */
public record ItemFavorEntry(
        String description,
        AttributeDomain requiredAttributeDomain,
        int requiredAttributeValue,
        List<ItemBonusEntry> bonuses,
        String additionalEffects
) {
}
