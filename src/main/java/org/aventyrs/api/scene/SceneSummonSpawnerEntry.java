package org.aventyrs.api.scene;

import java.util.List;

/**
 * Something that invokes a creature for its caster at each Rodada boundary while it lasts — Totem de Gaea (client
 * 0.0.95), the server-side mirror of core's {@code scene.SummonSpawner}. Each creature is a {@code NatureSummon} of
 * {@code kind} at {@code conjuradorManaGraduation}, lasting {@code summonRounds}.
 */
public record SceneSummonSpawnerEntry(String casterCharacterSheetId, int remainingRounds, String kind,
                                      int conjuradorManaGraduation, List<String> powers, Integer summonRounds) {

    SceneSummonSpawnerEntry ticked() {
        return new SceneSummonSpawnerEntry(casterCharacterSheetId, remainingRounds - 1, kind, conjuradorManaGraduation,
                powers, summonRounds);
    }
}
