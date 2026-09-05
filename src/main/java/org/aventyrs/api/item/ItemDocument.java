package org.aventyrs.api.item;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aventyrs.core.item.ItemCategory;
import org.aventyrs.core.item.ItemRarity;
import org.aventyrs.core.item.ItemWeightClass;
import org.aventyrs.core.item.RegaliaGrade;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Persisted state backing one core {@code AbstractItem} — a genuine forged/owned copy of a piece
 * of Equipamento, as opposed to a stateless catalog {@code ItemTemplate} (e.g. {@code ArmorItem}),
 * which needs no persistence of its own since it's fully reconstructible from its enum constant
 * name alone. A copy carries real mutable state — {@code damageTaken}, a fitted Obra-Prima and
 * Aprimoramentos, a socketed Pedra do Poder — so it lives in its own {@code items} collection,
 * referenced by id from {@code CharacterEntry#equipment} (worn/wielded) and {@code
 * CharacterSheetDocument#inventory}/{@code MonsterSheetDocument#inventory} (carried), rather than
 * embedded as a catalog constant name the way an unforged template reference still could be.
 *
 * <p>Fields mirror {@code AbstractItem}'s own one-for-one: {@code templateName} is the catalog
 * {@code ItemTemplate} constant this copy was forged from (its enum constant's own {@code
 * name()}), or {@code null} for a one-off item built without one — the same "polymorphic type as
 * a name string" convention {@code CharacterEntry#feats}/{@code #equipment} already use, since no
 * single Java type spans every {@code ItemTemplate} implementer (currently {@code ArmorItem}/
 * {@code NaturalWeapon}). {@code activeAbilityName} does the same for the {@code ItemActiveAbility}
 * a Regalia may carry. {@code favor}/{@code masterpiece}/{@code improvements}/{@code powerStone}
 * mirror core's own concrete (non-polymorphic-at-the-leaf) shapes directly — see each entry
 * type's own javadoc. {@code producedByCharacterId}/{@code improvementEffectSceneId} are kept as
 * plain opaque ids, same as {@code CharacterSheetDocument#playerId}: nothing here resolves them
 * against another collection.
 */
@Document(collection = "items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ItemDocument {

    @Id
    private String id;

    private String templateName;

    private String name;

    private String description;

    private ItemCategory category;

    private ItemRarity rarity;

    private ItemWeightClass weightClass;

    private int price;

    private int physicalDefenseBonus;

    private int magicDefenseBonus;

    private int hardness;

    private int damageTaken;

    private int castingBonus;

    private ItemFavorEntry favor;

    private ItemMasterpieceEntry masterpiece;

    private List<ItemImprovementEntry> improvements;

    private PowerStoneEntry powerStone;

    private RegaliaGrade regaliaGrade;

    private String activeAbilityName;

    private String producedByCharacterId;

    private boolean donatedByAventyr;

    private String improvementEffectSceneId;

    private int improvementEffectLastActiveRound;
}
