package org.aventyrs.api.scene.dto;

import java.util.List;

/**
 * {@code POST /api/scenes/{id}/summon-spawners} — Totem de Gaea (client 0.0.95): one creature now, one per Rodada.
 * Since core 0.1.6.26 rule may name {@code FESTIM}, {@code FESTIM_HORDA} or {@code CUSPIDOR} ({@code
 * SceneSummonSpawnerEntry}); summonLimit is the caster's "número máximo de invocações" (their Foco) for Festim, which the
 * caster's client knows and the server does not.
 */
public record SummonSpawnerCreateRequest(String casterCharacterSheetId, int rounds, String kind,
                                         int conjuradorManaGraduation, List<String> powers, Integer summonRounds,
                                         org.aventyrs.core.magic.invocation.SummonEnhancement enhancement,
                                         String rule, Integer summonLimit) {

    /** A single-creature spawner — the request shape before core 0.1.6.26. */
    public SummonSpawnerCreateRequest(String casterCharacterSheetId, int rounds, String kind,
                                      int conjuradorManaGraduation, List<String> powers, Integer summonRounds,
                                      org.aventyrs.core.magic.invocation.SummonEnhancement enhancement) {
        this(casterCharacterSheetId, rounds, kind, conjuradorManaGraduation, powers, summonRounds, enhancement, null,
                null);
    }

    /** No enhancement — the request shape before core 0.1.3. */
    public SummonSpawnerCreateRequest(String casterCharacterSheetId, int rounds, String kind,
                                      int conjuradorManaGraduation, List<String> powers, Integer summonRounds) {
        this(casterCharacterSheetId, rounds, kind, conjuradorManaGraduation, powers, summonRounds, null);
    }
}
