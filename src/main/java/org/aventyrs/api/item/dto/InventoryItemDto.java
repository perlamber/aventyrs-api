package org.aventyrs.api.item.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.aventyrs.core.item.ItemCategory;
import org.aventyrs.core.item.ItemRarity;
import org.aventyrs.core.item.ItemWeightClass;
import org.aventyrs.core.item.RegaliaGrade;

/**
 * Wire shape of one carried inventory item — see {@code InventoryItemEntry} for the persisted
 * shape this mirrors, and {@code ItemResponse} for the near-identical standalone-copy shape.
 */
public record InventoryItemDto(
        String templateName,
        String name,
        String description,
        @NotNull ItemCategory category,
        @NotNull ItemRarity rarity,
        @NotNull ItemWeightClass weightClass,
        int price,
        int physicalDefenseBonus,
        int magicDefenseBonus,
        int hardness,
        int damageTaken,
        int castingBonus,
        @Valid ItemFavorDto favor,
        @Valid ItemMasterpieceDto masterpiece,
        List<@Valid ItemImprovementDto> improvements,
        @Valid PowerStoneDto powerStone,
        RegaliaGrade regaliaGrade,
        String activeAbilityName,
        boolean donatedByAventyr
) {
}
