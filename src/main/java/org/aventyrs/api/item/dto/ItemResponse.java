package org.aventyrs.api.item.dto;

import java.util.List;
import org.aventyrs.core.item.ItemCategory;
import org.aventyrs.core.item.ItemRarity;
import org.aventyrs.core.item.ItemWeightClass;
import org.aventyrs.core.item.RegaliaGrade;

/**
 * {@code improvementEffectSceneId}/{@code improvementEffectLastActiveRound} are read-only here —
 * no endpoint sets them yet, since nothing in this API drives {@code
 * AbstractItem#activateImprovementEffect} (a live Cena concern) so far. They stay at their
 * default ({@code null}/0) until something does.
 */
public record ItemResponse(
        String id,
        String templateName,
        String name,
        String description,
        ItemCategory category,
        ItemRarity rarity,
        ItemWeightClass weightClass,
        int price,
        int physicalDefenseBonus,
        int magicDefenseBonus,
        int hardness,
        int damageTaken,
        int castingBonus,
        ItemFavorDto favor,
        ItemMasterpieceDto masterpiece,
        List<ItemImprovementDto> improvements,
        PowerStoneDto powerStone,
        RegaliaGrade regaliaGrade,
        String activeAbilityName,
        String producedByCharacterId,
        boolean donatedByAventyr,
        String improvementEffectSceneId,
        int improvementEffectLastActiveRound
) {
}
