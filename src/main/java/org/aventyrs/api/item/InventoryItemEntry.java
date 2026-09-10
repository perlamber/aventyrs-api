package org.aventyrs.api.item;

import java.util.List;
import org.aventyrs.core.item.ItemCategory;
import org.aventyrs.core.item.ItemRarity;
import org.aventyrs.core.item.ItemWeightClass;
import org.aventyrs.core.item.RegaliaGrade;

/**
 * Persisted mirror of one core {@code org.aventyrs.core.item.Item} carried in a combatant's
 * inventory — the same forged-copy shape {@link ItemDocument} holds, but <b>embedded</b> in the
 * sheet document rather than referenced by id from its own {@code items} collection.
 *
 * <p>The {@code items} collection ({@code /api/items}, {@link ItemDocument}) stays the GM's own
 * tool for authoring standalone item copies; a character's own inventory is state of that
 * character and belongs on its sheet, so it round-trips here instead of as a list of foreign ids.
 * A client rebuilds a real {@code Item} from one of these the same way it rebuilds one from an
 * {@code ItemResponse}; this API only stores and returns the shape (see {@link InventoryItemMapper}).
 *
 * <p>Fields mirror {@code AbstractItem}'s own, exactly as {@link ItemDocument}'s do — see that
 * class's javadoc for why {@code templateName}/{@code activeAbilityName} stay plain strings and
 * {@code favor}/{@code masterpiece}/{@code improvements}/{@code powerStone} reuse the same entry
 * types. The per-Cena {@code improvementEffect*} fields {@link ItemDocument} carries are omitted:
 * nothing drives them for an embedded copy any more than it does for a standalone one.
 */
public record InventoryItemEntry(
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
        ItemFavorEntry favor,
        ItemMasterpieceEntry masterpiece,
        List<ItemImprovementEntry> improvements,
        PowerStoneEntry powerStone,
        RegaliaGrade regaliaGrade,
        String activeAbilityName,
        boolean donatedByAventyr
) {
}
