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
 *
 * <p>{@code lockedHitPoints}/{@code lifeStealLockedHitPoints} are the part of {@code hitPointsSpent}
 * only a Descanso Verdadeiro recovers — core's {@code CombatantSheet#payWithVitality} lock
 * (Transferir Vitalidade, Força Excessiva) and its {@code lockDamage(n, true)} lock that Roubo de Vida
 * may also recover (Feridas Ardentes). {@code null} means "not reported", never "clear it".
 *
 * <p>{@code restScopedUses} is core's {@code CombatantSheet#getRestScopedUses} per source name — uses
 * spent until a Descanso Longo Verdadeiro clears them (Criar Refúgio's negations). {@code null} leaves
 * the stored map alone; a map replaces it, so a source cleared by a rest is sent as absent.
 *
 * <p>{@code egoLedger} is the Ego state beyond {@code temporaryEgoPoints} (core 0.0.76: permanent spent, extras,
 * overflow received). {@code null} leaves the stored one alone.
 */
public record CharacterStatusMessage(String characterSheetId, int hitPointsSpent,
                                     int magicPointsSpent, int determinationPointsSpent,
                                     CharacterStatus status,
                                     java.util.Map<org.aventyrs.core.character.EgoDomain, Integer> temporaryEgoPoints,
                                     java.util.List<org.aventyrs.api.sheet.dto.HourlyEgoRecoveryDto> hourlyEgoRecoveries,
                                     Boolean exhausted,
                                     Integer lockedHitPoints,
                                     Integer lifeStealLockedHitPoints,
                                     java.util.Map<String, Integer> restScopedUses,
                                     org.aventyrs.api.sheet.dto.EgoLedgerDto egoLedger) {

    /** A frame from before the Ego ledger (core 0.0.76) — the stored one is left alone. */
    public CharacterStatusMessage(String characterSheetId, int hitPointsSpent, int magicPointsSpent,
            int determinationPointsSpent, CharacterStatus status,
            java.util.Map<org.aventyrs.core.character.EgoDomain, Integer> temporaryEgoPoints,
            java.util.List<org.aventyrs.api.sheet.dto.HourlyEgoRecoveryDto> hourlyEgoRecoveries, Boolean exhausted,
            Integer lockedHitPoints, Integer lifeStealLockedHitPoints, java.util.Map<String, Integer> restScopedUses) {
        this(characterSheetId, hitPointsSpent, magicPointsSpent, determinationPointsSpent, status,
                temporaryEgoPoints, hourlyEgoRecoveries, exhausted, lockedHitPoints, lifeStealLockedHitPoints,
                restScopedUses, null);
    }

    /** A frame from before rest-scoped uses were carried — the stored map is left alone. */
    public CharacterStatusMessage(String characterSheetId, int hitPointsSpent, int magicPointsSpent,
            int determinationPointsSpent, CharacterStatus status,
            java.util.Map<org.aventyrs.core.character.EgoDomain, Integer> temporaryEgoPoints,
            java.util.List<org.aventyrs.api.sheet.dto.HourlyEgoRecoveryDto> hourlyEgoRecoveries, Boolean exhausted,
            Integer lockedHitPoints, Integer lifeStealLockedHitPoints) {
        this(characterSheetId, hitPointsSpent, magicPointsSpent, determinationPointsSpent, status,
                temporaryEgoPoints, hourlyEgoRecoveries, exhausted, lockedHitPoints, lifeStealLockedHitPoints, null);
    }

    /** A status frame from before core 0.0.70's PV locks — both left as stored. */
    public CharacterStatusMessage(String characterSheetId, int hitPointsSpent, int magicPointsSpent,
            int determinationPointsSpent, CharacterStatus status,
            java.util.Map<org.aventyrs.core.character.EgoDomain, Integer> temporaryEgoPoints,
            java.util.List<org.aventyrs.api.sheet.dto.HourlyEgoRecoveryDto> hourlyEgoRecoveries, Boolean exhausted) {
        this(characterSheetId, hitPointsSpent, magicPointsSpent, determinationPointsSpent, status,
                temporaryEgoPoints, hourlyEgoRecoveries, exhausted, null, null);
    }

    /**
     * The damage and pools alone — what every client sent before a Frenesi could spend Autocontrole.
     * The three Ego fields ({@code null}) are then left as stored.
     */
    public CharacterStatusMessage(String characterSheetId, int hitPointsSpent, int magicPointsSpent,
            int determinationPointsSpent, CharacterStatus status) {
        this(characterSheetId, hitPointsSpent, magicPointsSpent, determinationPointsSpent, status, null, null, null,
                null, null);
    }
}
