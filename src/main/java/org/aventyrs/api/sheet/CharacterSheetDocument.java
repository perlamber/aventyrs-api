package org.aventyrs.api.sheet;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aventyrs.api.item.InventoryItemEntry;
import org.aventyrs.core.character.EgoDomain;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Persisted state backing an {@code org.aventyrs.core.sheet.CharacterSheet}. {@code character} is
 * embedded directly (see {@link CharacterEntry}) rather than referenced by id — Mongo's document
 * model makes that natural, and nothing modifies a Character independently of the sheet wrapping
 * it. {@code player} is still kept as a plain id ({@link #playerId}) since Players are a
 * genuinely independent, shared resource (one Player may own several CharacterSheets).
 */
@Document(collection = "characterSheets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CharacterSheetDocument {

    @Id
    private String id;

    private CharacterEntry character;

    private String playerId;

    private BigDecimal totalExperience;

    private BigDecimal unUsedExperience;

    private int hitPointsSpent;

    private int magicPointsSpent;

    private int determinationPointsSpent;

    private int shieldPoints;

    private int famaPositiva;

    private int famaNegativa;

    /** Pontos de Equipamento — the budget an item-store purchase spends. 0 for a sheet persisted
     * before it existed, matching core's own {@code AbstractCombatantSheet} default. */
    private int equipmentPoints;

    private Map<EgoDomain, Integer> temporaryEgoPoints;

    private List<TemporaryBonusEntry> temporaryBonuses;

    private List<BleedingEntry> bleedingEffects;

    private List<ManaDrainEntry> manaDrains;

    private List<WitheringEntry> witheringEffects;

    private List<PendingEgoRecoveryEntry> pendingEgoRecoveries;

    private List<LifeStealEntry> lifeSteals;


    /** Every {@code org.aventyrs.core.item.Item} this sheet carries, embedded — see {@link InventoryItemEntry}.
     * {@code null} on a document persisted before structured inventory existed (was {@code List<String>}). */
    private List<InventoryItemEntry> inventory;

    /** Null until set via update; the image itself is uploaded separately through {@code /api/images},
     * same convention as {@code SceneDocument#getImageUrl()}. */
    private String tokenImageUrl;

    /**
     * The one Campanha this sheet takes part in, or {@code null} for none. This field is the
     * membership record. Only {@code CampaignService}'s participant endpoints write it: {@code PUT
     * /character-sheets/{id}} leaves it alone. {@code null} on every document persisted before it
     * existed, which reads correctly as "in no Campanha".
     */
    private String campaignId;

    /**
     * Temporary Ego points owed back by the hour (Frenesi's Autocontrole). Written only by the live
     * combat-status path, never by the full-overwrite PUT, so a PUT built by a client that predates it
     * cannot wipe it. {@code null} on every document written before it existed.
     */
    private List<HourlyEgoRecoveryEntry> hourlyEgoRecoveries;

    /** Uno com a Ira's exhaustion, waiting for a Descanso Curto Verdadeiro. Boxed: absent on older documents. */
    private Boolean exhausted;
}
