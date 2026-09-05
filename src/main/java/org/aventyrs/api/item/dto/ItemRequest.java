package org.aventyrs.api.item.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.aventyrs.core.item.ItemCategory;
import org.aventyrs.core.item.ItemRarity;
import org.aventyrs.core.item.ItemWeightClass;
import org.aventyrs.core.item.RegaliaGrade;

/**
 * Creates or fully replaces one forged item copy. {@code templateName}/{@code activeAbilityName}
 * are the catalog constant's own {@code name()} (see {@code ItemDocument} for why these stay
 * plain strings); {@code favor}/{@code masterpiece}/{@code improvements}/{@code powerStone} are
 * nullable/empty exactly when the copy carries none.
 */
public record ItemRequest(
        String templateName,
        @NotBlank String name,
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
        String producedByCharacterId,
        boolean donatedByAventyr
) {
}
