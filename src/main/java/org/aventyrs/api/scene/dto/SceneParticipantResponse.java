package org.aventyrs.api.scene.dto;

import java.util.UUID;

/** {@code joinedAtRound}: see {@code SceneParticipantEntry} — a participant is in the turn
 * rotation exactly when it is {@code <=} the scene's {@code currentRound}. {@code concealment} is
 * {@code null} unless the participant is currently Escondido — see {@code SceneConcealmentEntry}.
 * {@code initiativeOverride} is the Iniciativa an Ego point put in place of {@code initiativeValue}, which the turn
 * order sorts by while it holds; {@code null} when none does. {@code initiativeOverrideRodadas} is how many more
 * Rodadas it governs ({@code null}: the rest of the Cena) and {@code initiativeOverrideStarted} whether a Rodada
 * boundary has passed since it was set — what a client rebuilding its own copy of the order needs. */
public record SceneParticipantResponse(
        String characterSheetId,
        int initiativeValue,
        UUID group,
        GridPositionDto position,
        int joinedAtRound,
        ConcealmentDto concealment,
        Integer initiativeOverride,
        Integer initiativeOverrideRodadas,
        Boolean initiativeOverrideStarted
) {
}
