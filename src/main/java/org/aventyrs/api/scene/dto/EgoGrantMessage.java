package org.aventyrs.api.scene.dto;

import org.aventyrs.core.character.EgoDomain;

/**
 * Sent to {@code /app/scenes/{sceneId}/ego-grants} by the Narrador's client when a PdN's Efeito de Ego owes every PJ a
 * temporary point in {@code domain} (core 0.0.83 — "em vez de gastar pontos, todos os PJs recebem um ponto temporário
 * neste mesmo Ego"). Relayed, never persisted: each client grants it to the PJs it controls and saves those sheets.
 */
public record EgoGrantMessage(EgoDomain domain) {
}
