package org.aventyrs.api.sheet;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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

    private Map<EgoDomain, Integer> temporaryEgoPoints;

    private List<TemporaryBonusEntry> temporaryBonuses;

    private List<BleedingEntry> bleedingEffects;

    private List<ManaDrainEntry> manaDrains;

    private List<WitheringEntry> witheringEffects;

    private List<PendingEgoRecoveryEntry> pendingEgoRecoveries;

    private List<LifeStealEntry> lifeSteals;

    /** Item catalog constant names (e.g. {@code "ROUPA_PESADA"}) — see {@link CharacterEntry}'s equipment javadoc. */
    private List<String> inventory;
}
