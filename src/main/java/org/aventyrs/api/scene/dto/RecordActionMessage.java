package org.aventyrs.api.scene.dto;

import org.aventyrs.api.scene.AttackSourceKind;
import org.aventyrs.core.character.AttributeDomain;
import org.aventyrs.core.sheet.ActionCost;
import org.aventyrs.core.skill.CriticalResult;
import org.aventyrs.core.skill.DifficultyLevel;
import org.aventyrs.core.skill.SkillType;

/**
 * Inbound STOMP payload for {@code /app/scenes/{sceneId}/actions} — one {@code CombatantAction}
 * the sending client already resolved, exactly as {@link TokenMoveMessage}'s {@code position} or
 * {@link CharacterStatusMessage}'s {@code status} are taken at face value here: this API persists
 * what the rules engine decided, it doesn't re-run it.
 *
 * <p>{@code costKind}/{@code actionPoints} mirror core's {@code ActionCost} — {@code
 * actionPoints} is only meaningful, and must be {@code >= 1}, for {@link ActionCost.Kind
 * #ACTION_POINTS}; a mismatched pair is rejected the same silent way {@link
 * org.aventyrs.api.scene.SceneRealtimeController#move} rejects an invalid move. {@code succeeded}/
 * {@code margin}/{@code criticalResult}/{@code reachedDifficultyLevel} mirror core's {@code
 * ActionOutcome} and are left {@code null} together when the roll wasn't made against anything
 * stated. {@code attackSourceKind} is {@code null} unless the action was an attack.
 */
public record RecordActionMessage(
        String characterSheetId,
        SkillType skill,
        AttributeDomain governingDomain,
        AttackSourceKind attackSourceKind,
        ActionCost.Kind costKind,
        int actionPoints,
        int turnNumber,
        Boolean succeeded,
        Integer margin,
        CriticalResult criticalResult,
        DifficultyLevel reachedDifficultyLevel) {
}
