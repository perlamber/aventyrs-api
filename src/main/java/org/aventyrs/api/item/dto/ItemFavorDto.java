package org.aventyrs.api.item.dto;

import jakarta.validation.Valid;
import java.util.List;
import org.aventyrs.core.character.AttributeDomain;

/** See {@code ItemFavorEntry} for the persisted shape this mirrors. */
public record ItemFavorDto(
        String description,
        AttributeDomain requiredAttributeDomain,
        int requiredAttributeValue,
        List<@Valid ItemBonusDto> bonuses,
        String additionalEffects
) {
}
