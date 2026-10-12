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
        boolean donatedByAventyr,
        Integer quantity,
        List<String> loadedProjectiles
) {

    /** The shape before core 0.1.6.6 — see {@code InventoryItemEntry}'s matching constructor. */
    public InventoryItemDto(String templateName, String name, String description, ItemCategory category,
                            ItemRarity rarity, ItemWeightClass weightClass, int price, int physicalDefenseBonus,
                            int magicDefenseBonus, int hardness, int damageTaken, int castingBonus,
                            ItemFavorDto favor, ItemMasterpieceDto masterpiece, List<ItemImprovementDto> improvements,
                            PowerStoneDto powerStone, RegaliaGrade regaliaGrade, String activeAbilityName,
                            boolean donatedByAventyr) {
        this(templateName, name, description, category, rarity, weightClass, price, physicalDefenseBonus,
                magicDefenseBonus, hardness, damageTaken, castingBonus, favor, masterpiece, improvements, powerStone,
                regaliaGrade, activeAbilityName, donatedByAventyr, null, null);
    }
}
