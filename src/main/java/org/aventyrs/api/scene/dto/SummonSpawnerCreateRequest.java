package org.aventyrs.api.scene.dto;

import java.util.List;

/** {@code POST /api/scenes/{id}/summon-spawners} — Totem de Gaea (client 0.0.95): one creature now, one per Rodada. */
public record SummonSpawnerCreateRequest(String casterCharacterSheetId, int rounds, String kind,
                                         int conjuradorManaGraduation, List<String> powers, Integer summonRounds) {
}
