package org.aventyrs.api.scene.dto;

import java.time.Instant;
import java.util.List;

import org.aventyrs.api.scene.RollRequestKind;
import org.aventyrs.core.skill.DifficultyLevel;
import org.aventyrs.core.skill.SkillType;

/**
 * A roll the Narrador has asked for, broadcast on {@code /topic/scenes/{sceneId}/roll-requests} so
 * every client shows it — not only the clients that must answer. That is the point: someone else at
 * the table may want to react to an attack aimed at somebody's character.
 *
 * <p>The persisted mirror of {@code SceneRollRequestEntry}, flattened for the wire.
 */
public record RollRequestedEvent(
        String requestId,
        RollRequestKind kind,
        SkillType skill,
        DifficultyLevel difficultyLevel,
        int attackBonus,
        String attackerCharacterSheetId,
        List<String> targetCharacterSheetIds,
        String prompt,
        Instant requestedAt) {
}
