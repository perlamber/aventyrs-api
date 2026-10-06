package org.aventyrs.api.scene;

import java.util.List;

/**
 * Something that invokes a creature for its caster at each Rodada boundary while it lasts — Totem de Gaea (client
 * 0.0.95), the server-side mirror of core's {@code scene.SummonSpawner}. Each creature is a {@code NatureSummon} of
 * {@code kind} at {@code conjuradorManaGraduation}, lasting {@code summonRounds}, carrying {@code enhancement} — what
 * its caster's Títulos give each one (Bruxo, core 0.1.3), {@code null} for none.
 */
public record SceneSummonSpawnerEntry(String casterCharacterSheetId, int remainingRounds, String kind,
                                      int conjuradorManaGraduation, List<String> powers, Integer summonRounds,
                                      org.aventyrs.core.magic.invocation.SummonEnhancement enhancement) {

    /** A spawner stored before core 0.1.3 — its creatures carry no enhancement. */
    public SceneSummonSpawnerEntry(String casterCharacterSheetId, int remainingRounds, String kind,
                                   int conjuradorManaGraduation, List<String> powers, Integer summonRounds) {
        this(casterCharacterSheetId, remainingRounds, kind, conjuradorManaGraduation, powers, summonRounds, null);
    }

    SceneSummonSpawnerEntry ticked() {
        return new SceneSummonSpawnerEntry(casterCharacterSheetId, remainingRounds - 1, kind, conjuradorManaGraduation,
                powers, summonRounds, enhancement);
    }
}
