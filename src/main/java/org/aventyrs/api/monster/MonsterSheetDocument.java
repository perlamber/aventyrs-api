package org.aventyrs.api.monster;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.aventyrs.api.item.InventoryItemEntry;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aventyrs.api.sheet.BleedingEntry;
import org.aventyrs.api.sheet.CharacterEntry;
import org.aventyrs.api.sheet.LifeStealEntry;
import org.aventyrs.api.sheet.ManaDrainEntry;
import org.aventyrs.api.sheet.PendingEgoRecoveryEntry;
import org.aventyrs.api.sheet.TemporaryBonusEntry;
import org.aventyrs.api.sheet.WitheringEntry;
import org.aventyrs.core.character.EgoDomain;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Persisted state backing an {@code org.aventyrs.core.monster.MonsterSheet}. {@code character} is
 * embedded via the same {@link CharacterEntry} {@code CharacterSheetDocument} uses — a
 * {@code Character} is the same core type whichever combatant sheet wraps it (see core's {@code
 * CombatantSheet} javadoc). {@code playerId} mirrors {@code CharacterSheetDocument}'s own field —
 * core's {@code MonsterSheet.getPlayer()} is required for the same reason {@code
 * CharacterSheet.getPlayer()} is: someone at the table owns the stat block instance once it's
 * playing in a Cena. In practice that's the GM running the table rather than the player behind a
 * character, but this API doesn't enforce that distinction any harder than {@code PlayerRole}
 * enforces anything else here (see that enum's own javadoc). Unlike {@code CharacterSheetDocument}
 * there's still no experience/Fama fields: a foe never spends XP (see core's {@code MonsterSheet}
 * javadoc for why).
 *
 * <p><b>{@code blueprint} is the source of truth</b> (core 0.0.59): the Mestre's choices, from which
 * core's {@code MonsterRules} derives every GD, Defesa, PA and PV/PD/PM on each read. {@code
 * character} is <i>derived</i> from it too — rewritten on every create and update, never edited on
 * its own — and kept only so the readers that predate blueprints (a Scene's participant lookup,
 * the Campanha's bag) find the same shape a character sheet has.
 */
@Document(collection = "monsterSheets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MonsterSheetDocument {

    @Id
    private String id;

    /** The Mestre's choices — core derives every number from this. */
    private MonsterBlueprintEntry blueprint;

    /** Derived from {@link #blueprint} on every write; see the class javadoc. */
    private CharacterEntry character;

    private String playerId;

    private int hitPointsSpent;

    private int magicPointsSpent;

    private int determinationPointsSpent;

    private int shieldPoints;

    private Map<EgoDomain, Integer> temporaryEgoPoints;

    private List<TemporaryBonusEntry> temporaryBonuses;

    private List<BleedingEntry> bleedingEffects;

    private List<ManaDrainEntry> manaDrains;

    private List<WitheringEntry> witheringEffects;

    private List<PendingEgoRecoveryEntry> pendingEgoRecoveries;

    private List<LifeStealEntry> lifeSteals;

    /**
     * What this foe carries, not wears — embedded, the same shape as {@code
     * CharacterSheetDocument#inventory}. It was once a list of {@code items}-collection ids that
     * nothing read or wrote; changelog 018 migrated it. A Saquear, or the end of combat, moves the
     * whole list into the Campanha's bag ({@code CampaignService#lootFrom}).
     */
    private List<InventoryItemEntry> inventory;

    /** Null until set via update; the image itself is uploaded separately through {@code /api/images},
     * same convention as {@code SceneDocument#getImageUrl()}. */
    private String tokenImageUrl;
}
