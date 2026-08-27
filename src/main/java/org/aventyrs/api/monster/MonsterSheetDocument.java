package org.aventyrs.api.monster;

import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
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
import org.aventyrs.core.effect.CriticalEffectType;
import org.aventyrs.core.skill.DifficultyLevel;
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
 * javadoc for why). {@code physicalDefense}/{@code magicDefense}/{@code attackDifficulty}/{@code
 * attackBonus}/{@code undead}/{@code criticalEffectImmunities} mirror core's {@code MonsterSheet}'s
 * own fields, authored on the stat block rather than derived.
 */
@Document(collection = "monsterSheets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MonsterSheetDocument {

    @Id
    private String id;

    private CharacterEntry character;

    private String playerId;

    private int physicalDefense;

    private int magicDefense;

    private DifficultyLevel attackDifficulty;

    private int attackBonus;

    private boolean undead;

    private Set<CriticalEffectType> criticalEffectImmunities;

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

    /** Item catalog constant names (e.g. {@code "ROUPA_PESADA"}) — see {@link CharacterEntry}'s equipment javadoc. */
    private List<String> inventory;

    /** Null until set via update; the image itself is uploaded separately through {@code /api/images},
     * same convention as {@code SceneDocument#getImageUrl()}. */
    private String tokenImageUrl;
}
