package org.aventyrs.api.item;

import org.aventyrs.core.item.PowerStoneImprovement;
import org.aventyrs.core.item.PowerStoneMasterpiece;
import org.aventyrs.core.item.PowerStoneQuality;
import org.aventyrs.core.item.PowerStoneType;

/**
 * Persisted mirror of a core {@code PowerStone} — the Pedra do Poder socketed into one item
 * copy, requiring the Encaixe Aprimoramento fitted to its host. {@code masterpiece}/{@code
 * improvement} are the stone's own optional Obra-Prima/Aprimoramento refinements, independent of
 * the host item's own {@link ItemMasterpieceEntry}/{@link ItemImprovementEntry}.
 */
public record PowerStoneEntry(
        PowerStoneType type,
        PowerStoneQuality quality,
        PowerStoneMasterpiece masterpiece,
        PowerStoneImprovement improvement
) {
}
