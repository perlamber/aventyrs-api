package org.aventyrs.api.scene.dto;

import org.aventyrs.core.skill.DifficultyLevel;

/**
 * Wire shape of a participant concealment change — broadcast on {@code
 * /topic/scenes/{sceneId}/hidden} to every client watching the scene, including the one that sent
 * the {@link HiddenStatusMessage} that produced it, the same echo-confirms-it flow {@link
 * CharacterStatusChangedEvent} takes for a combat-status tier. See {@link HiddenStatusMessage}'s
 * own javadoc for what {@code ordinaryConcealmentValue}/{@code expertConcealmentValue} and {@code
 * difficultyLevel}/{@code bonus} carry.
 */
public record HiddenStatusChangedEvent(String characterSheetId, boolean hidden,
        Integer ordinaryConcealmentValue, Integer expertConcealmentValue,
        DifficultyLevel difficultyLevel, Integer bonus) {
}
