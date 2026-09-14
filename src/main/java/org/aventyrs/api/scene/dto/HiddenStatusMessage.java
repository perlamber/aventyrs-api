package org.aventyrs.api.scene.dto;

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
 * <p>Unlike {@link CharacterStatusMessage}, this is never persisted — see {@code
 * org.aventyrs.api.scene.SceneRealtimeController#hidden} for why a concealment is closer to
 * {@link ScenePingMessage}'s ephemeral marker than to a character sheet's own combat state.
 */
public record HiddenStatusMessage(String characterSheetId, boolean hidden,
        Integer ordinaryConcealmentValue, Integer expertConcealmentValue) {
}
