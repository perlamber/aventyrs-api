package org.aventyrs.api.item;

import org.aventyrs.core.character.DefenseType;
import org.aventyrs.core.item.DefensiveImprovement;
import org.aventyrs.core.magic.ElementalType;

/**
 * Persisted mirror of a core {@code ItemImprovement} — one Aprimoramento fitted to one item
 * copy. {@code AbstractItem#addImprovement} only ever accepts an {@code ItemImprovement}
 * wrapping a {@link DefensiveImprovement} definition (a bare {@code DefensiveImprovement} is
 * rejected), so this is the only shape a forged item's improvement takes. {@code
 * selectedDefense} is set only for {@code DefensiveImprovement#CAMADA_DE_REFORCO} on a
 * non-shield host, {@code selectedElementalType} only for {@code
 * DefensiveImprovement#BENCAO_ELEMENTAL} — {@code null} otherwise.
 */
public record ItemImprovementEntry(
        DefensiveImprovement definition,
        DefenseType selectedDefense,
        ElementalType selectedElementalType
) {
}
