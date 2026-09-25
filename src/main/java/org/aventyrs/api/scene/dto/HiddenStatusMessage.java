package org.aventyrs.api.scene.dto;

import org.aventyrs.core.skill.DifficultyLevel;

/**
 * Inbound STOMP payload for {@code /app/scenes/{sceneId}/hidden} — a participant's Esconder-se
 * concealment starting ({@code hidden} {@code true}) or ending ({@code false}), resolved
 * client-side by aventyrs-core's {@code HidingService} and taken at face value here, the same
 * "this API persists/relays what the rules engine decided" stance {@link CharacterStatusMessage}
 * takes for a combat-status tier.
 *
 * <p>{@code ordinaryConcealmentValue}/{@code expertConcealmentValue} are the flattened GD an
 * observing client's own Atenção roll must reach to see through the concealment — {@code
 * org.aventyrs.core.sheet.Hidden#getConcealmentValue()}/{@code #getConcealmentValue(true)} on the
 * hiding client's own sheet — carried opaquely through this relay so every other client can
 * resolve its own "did I spot them" locally, without this API needing to know what a Grau de
 * Dificuldade even is. Both {@code null} on a reveal.
 *
 * <p>{@code difficultyLevel}/{@code bonus} are the tier-and-modifier pair the hiding client's
 * {@code Hidden} holds — what lets the client that controls this participant rebuild the Condição
 * on its own core sheet after a reconnect. All four are {@code null} on a reveal.
 *
 * <p>Persisted onto the participant as well as relayed — see {@code
 * org.aventyrs.api.scene.SceneRealtimeController#hidden}.
 */
public record HiddenStatusMessage(String characterSheetId, boolean hidden,
        Integer ordinaryConcealmentValue, Integer expertConcealmentValue,
        DifficultyLevel difficultyLevel, Integer bonus) {

    /** The concealment this message sets, or {@code null} for a reveal (or a hide missing its GD). */
    public ConcealmentDto toConcealment() {
        if (!hidden || difficultyLevel == null || bonus == null
                || ordinaryConcealmentValue == null || expertConcealmentValue == null) {
            return null;
        }
        return new ConcealmentDto(difficultyLevel, bonus, ordinaryConcealmentValue, expertConcealmentValue);
    }
}
