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
 * Persisted state backing an {@code org.aventyrs.core.sheet.CharacterSheet}. {@code character}
 * and {@code player} references are kept as plain ids ({@link #characterId}, {@link #playerId})
 * rather than embedding the core domain objects, since {@code Character} CRUD doesn't exist yet
 * and the core module deliberately carries no persistence/web framework coupling.
 */
@Document(collection = "characterSheets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CharacterSheetDocument {

    @Id
    private String id;

    private String characterId;

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
}
