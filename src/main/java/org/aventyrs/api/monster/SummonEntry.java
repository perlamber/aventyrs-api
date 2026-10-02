package org.aventyrs.api.monster;

import java.util.List;

/**
 * What an invoked creature is (client 0.0.95) — persisted on its {@link MonsterSheetDocument} in place of a blueprint.
 * Core rebuilds it with {@code NatureSummon#restore}: {@code kind} a {@code NatureSummonKind}, the Conjurador's
 * Graduação in Domínio do Mana, and the {@code LacertoPower}s rolled at the cast. {@code casterCharacterSheetId} is who
 * invoked it; the document's {@code playerId} is the caster's player, who controls it (table ruling, 2026-10-01).
 */
public record SummonEntry(String kind, int conjuradorManaGraduation, List<String> powers,
                          String casterCharacterSheetId) {
}
