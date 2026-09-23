package org.aventyrs.api.scene;

import java.util.List;
import org.aventyrs.core.character.AttributeDomain;
import org.aventyrs.core.sheet.ActionCost;
import org.aventyrs.core.sheet.ActionOutcome;
import org.aventyrs.core.skill.SkillType;

/**
 * Persisted mirror of one entry in core's {@code org.aventyrs.core.scene.Scene#getActionHistory()}
 * — a {@code org.aventyrs.core.sheet.CombatantAction} paired with the combatant that took it,
 * same pairing as core's own {@code org.aventyrs.core.scene.SceneAction}. Referenced by {@code
 * characterSheetId} rather than embedding the combatant, same reasoning as {@link
 * SceneParticipantEntry}.
 *
 * <p>This API never runs core's rules engine itself — the live {@code CombatantSheet}/{@code
 * Scene} exist only client-side (see {@link SceneService}'s own javadoc) — so an entry here is
 * reported by whichever client already resolved the roll, not re-derived from one. {@code cost}/
 * {@code outcome} reuse core's own {@link ActionCost}/{@link ActionOutcome} records directly,
 * same convention as {@link SceneParticipantEntry#position()} reusing core's {@code GridPosition}:
 * both are plain data carriers with no polymorphism to flatten away. {@code outcome} is {@code
 * null} when the roll wasn't resolved against anything stated. {@code attackSourceKind} is {@code
 * null} unless the action was an attack — see {@link AttackSourceKind}.
 *
 * <p>{@code dice}/{@code total} are the 3d6 faces and the final total the rolling client showed its
 * own player — what lets every other client's log render the same card, dice and all, instead of
 * a bare verdict. Both are {@code null} from a client that predates them.
 */
public record SceneActionEntry(
        String characterSheetId,
        SkillType skill,
        AttributeDomain governingDomain,
        AttackSourceKind attackSourceKind,
        ActionCost cost,
        int turnNumber,
        ActionOutcome outcome,
        List<Integer> dice,
        Integer total) {
}
