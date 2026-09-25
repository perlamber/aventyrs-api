package org.aventyrs.api.scene;

import org.aventyrs.core.skill.DifficultyLevel;

/**
 * Persisted mirror of a participant's Esconder-se concealment — aventyrs-core's {@code Hidden}: the
 * {@code difficultyLevel}/{@code bonus} pair it holds, plus the two flattened values an observer's
 * Atenção roll must reach ({@code Hidden#getConcealmentValue()}/{@code #getConcealmentValue(true)}).
 *
 * <p>Carried on {@link SceneParticipantEntry} so a concealment outlives the live {@code /hidden}
 * relay: a participant added hidden is withheld from every board in the same payload that adds it,
 * and a client joining later — or the one that controls a character the GM placed hidden — learns
 * of it from the Scene itself. The pair lets the controlling client rebuild the {@code Hidden} on its
 * own core sheet; the flattened values are what every other client compares its own Atenção
 * against. This API computes none of them — they are taken at face value from the client that
 * resolved the Furtividade, the same boundary {@code CharacterStatusMessage} keeps.
 */
public record SceneConcealmentEntry(
        DifficultyLevel difficultyLevel,
        int bonus,
        int ordinaryConcealmentValue,
        int expertConcealmentValue) {
}
