package org.aventyrs.api.scene.dto;

/**
 * Sent to {@code /app/scenes/{sceneId}/initiative} by the client that paid an Iniciativa Ego point to change a
 * participant's place in the order (core 0.0.79) — its own, or a PdN's. {@code rodadas} is how many Rodadas the
 * value governs, {@code null} for the rest of the Cena.
 */
public record InitiativeOverrideMessage(String characterSheetId, int value, Integer rodadas) {
}
