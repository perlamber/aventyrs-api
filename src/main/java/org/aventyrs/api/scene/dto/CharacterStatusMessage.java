package org.aventyrs.api.scene.dto;

import org.aventyrs.core.character.CharacterStatus;

/**
 * Wire shape of a participant's combat-state change — sent to {@code
 * /app/scenes/{sceneId}/status} by whichever client applied the damage.
 *
 * <p>{@code status} is computed client-side (aventyrs-core's {@code DamageService#applyDamage}
 * already refreshes it from {@code hitPointsSpent} against the character's max PV) and taken at
 * face value here, exactly as {@link TokenMoveMessage}'s {@code position} is: this API persists
 * what the rules engine decided, it doesn't re-run the rules. {@code hitPointsSpent} travels
 * alongside it rather than being derived, so the stored damage and the stored tier can never
 * disagree — a sheet reloaded later resolves the same status it was broadcast with.
 */
public record CharacterStatusMessage(String characterSheetId, int hitPointsSpent,
                                     int magicPointsSpent, int determinationPointsSpent,
                                     CharacterStatus status,
                                     java.util.Map<org.aventyrs.core.character.EgoDomain, Integer> temporaryEgoPoints,
                                     java.util.List<org.aventyrs.api.sheet.dto.HourlyEgoRecoveryDto> hourlyEgoRecoveries,
                                     Boolean exhausted) {

    /**
     * The damage and pools alone — what every client sent before a Frenesi could spend Autocontrole.
     * The three Ego fields ({@code null}) are then left as stored.
     */
    public CharacterStatusMessage(String characterSheetId, int hitPointsSpent, int magicPointsSpent,
            int determinationPointsSpent, CharacterStatus status) {
        this(characterSheetId, hitPointsSpent, magicPointsSpent, determinationPointsSpent, status, null, null, null);
    }
}
