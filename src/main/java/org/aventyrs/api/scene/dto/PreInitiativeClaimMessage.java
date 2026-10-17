package org.aventyrs.api.scene.dto;

/**
 * A combatant claiming Paranoia Saudável's Rodada 0.0 action at combat start (core 0.1.7.15) — sent to {@code
 * /app/scenes/{sceneId}/pre-initiative} by the client that owns the sheet, after it paid the 2PD on the live sheet.
 */
public record PreInitiativeClaimMessage(String characterSheetId) {
}
