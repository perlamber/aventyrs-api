package org.aventyrs.api.scene.dto;

/**
 * Inbound STOMP payload for {@code /app/scenes/{sceneId}/combatant-actions} — a combatant declaring an action on the
 * board (core 0.1.6.21). Relayed as is on {@code /topic/scenes/{sceneId}/combatant-actions} and never persisted, like
 * a ping: every other table writes it into its action history, where a holder of a Preparação may react to it (its
 * trigger is narrated, never checked).
 *
 * @param actorCharacterSheetId who acted
 * @param kind                  what kind of action
 * @param label                 what the actor's table called it — "Bola de Fogo", "moveu 4UD"
 */
public record CombatantActionMessage(String actorCharacterSheetId, Kind kind, String label) {

    /** The broad kind of action declared. */
    public enum Kind {
        MOVE,
        ATTACK,
        SPELL,
        ABILITY,
        MANOEUVRE,
        COMPETENCY,
        OTHER
    }

    /** Whether the message names an actor and a kind. */
    public boolean isWellFormed() {
        return actorCharacterSheetId != null && kind != null;
    }
}
