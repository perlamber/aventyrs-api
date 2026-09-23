package org.aventyrs.api.scene.dto;

import java.util.List;
import org.aventyrs.api.scene.AttackSourceKind;
import org.aventyrs.core.character.AttributeDomain;
import org.aventyrs.core.sheet.ActionCost;
import org.aventyrs.core.skill.CriticalResult;
import org.aventyrs.core.skill.DifficultyLevel;
import org.aventyrs.core.skill.SkillType;

/**
 * Wire shape of a persisted {@code org.aventyrs.api.scene.SceneActionEntry} — broadcast on {@code
 * /topic/scenes/{sceneId}/actions} after a {@link RecordActionMessage} is accepted, and the shape
 * {@code SceneResponse#actionHistory()} carries so a client joining a scene already in progress
 * sees every action recorded so far, not just the ones broadcast while it was connected.
 *
 * <p>{@code dice}/{@code total} are the 3d6 faces and the final total the rolling client showed its
 * own player — what lets every other client's log render the same card, dice and all, instead of
 * a bare verdict. Both are {@code null} from a client that predates them.
 */
public record SceneActionEvent(
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
        DifficultyLevel reachedDifficultyLevel,
        List<Integer> dice,
        Integer total) {
}
