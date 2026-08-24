package org.aventyrs.api.scene.dto;

import java.util.UUID;

/** {@code joinedAtRound}: see {@code SceneParticipantEntry} — a participant is in the turn
 * rotation exactly when it is {@code <=} the scene's {@code currentRound}. */
public record SceneParticipantResponse(
        String characterSheetId,
        int initiativeValue,
        UUID group,
        GridPositionDto position,
        int joinedAtRound
) {
}
