package org.aventyrs.api.analytics;

/**
 * What an {@link AnalyticsEventDocument} records — one constant per {@code SceneRealtimeController} handler the
 * warehouse reads. Ping, terrain, grid and Ego grants are left out: none of them says anything about a combatant.
 */
public enum AnalyticsEventType {
    ACTION,
    ABILITY,
    ROLL_REQUEST,
    ROLL_RESPONSE,
    DAMAGE,
    STATUS,
    CONDITION,
    SUBORDINATE,
    SPELL_LANDED,
    COMBATANT_STATE,
    TURN_ADVANCED,
    COMBAT_STARTED,
    COMBAT_ENDED,
    MOVE,
    HIDDEN,
    INITIATIVE,
    TIME
}
