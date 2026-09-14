package org.aventyrs.api.scene.dto;

/**
 * Wire shape of an (unpersisted) participant concealment change — broadcast on {@code
 * /topic/scenes/{sceneId}/hidden} to every client watching the scene, including the one that sent
 * the {@link HiddenStatusMessage} that produced it, the same echo-confirms-it flow {@link
 * CharacterStatusChangedEvent} takes for a combat-status tier. See {@link HiddenStatusMessage}'s
 * own javadoc for what {@code ordinaryConcealmentValue}/{@code expertConcealmentValue} carry.
 */
public record HiddenStatusChangedEvent(String characterSheetId, boolean hidden,
        Integer ordinaryConcealmentValue, Integer expertConcealmentValue) {
}
