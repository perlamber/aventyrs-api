package org.aventyrs.api.scene.dto;

import java.util.List;

import org.aventyrs.api.scene.RollRequestKind;
import org.aventyrs.core.skill.DifficultyLevel;
import org.aventyrs.core.skill.SkillType;

/**
 * The Narrador asking the table to roll — inbound on {@code /app/scenes/{sceneId}/roll-requests}.
 *
 * <p>No {@code requestId}: the server stamps one, so responses have something stable to point at
 * that no client could collide on.
 *
 * <p>Who sent this is not stated and could not be trusted if it were. This API has no
 * authentication and {@code PlayerRole} is explicitly "not an authorization boundary", so
 * Narrador-ness is a client-side notion here exactly as it is everywhere else — the same footing
 * {@code characterSheetId} already stands on in {@link TokenMoveMessage}.
 *
 * @param targetCharacterSheetIds empty or {@code null} means every participant may answer.
 */
public record RollRequestMessage(
        RollRequestKind kind,
        SkillType skill,
        DifficultyLevel difficultyLevel,
        int attackBonus,
        String attackerCharacterSheetId,
        List<String> targetCharacterSheetIds,
        String prompt) {
}
