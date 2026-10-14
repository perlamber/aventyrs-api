package org.aventyrs.api.scene;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The one Reação window open on a scene (core 0.1.6.17): an action paused where it provoked Reações — a movement at a
 * route checkpoint, a casting before its roll, an attack on an ally, a provoking manoeuvre — waiting for each named
 * reactor to pass or react. Persisted so a client that reconnects mid-window still sees it; cleared when it closes.
 *
 * @param kind        {@code MOVEMENT}, {@code CASTING}, {@code ATTACK_ON_ALLY} or {@code PROVOKING_MANOEUVRE}
 * @param actorId     whose action is paused
 * @param reactorIds  who may react
 * @param x           the hex the action is paused at (a mover's checkpoint, a caster's own hex)
 * @param elevation   its height in UD
 * @param description free text the actor's client wrote for the prompt
 * @param answers     reactorId to {@code PASS}/{@code REACT}, so far
 */
public record SceneReactionWindowEntry(String windowId, String kind, String actorId, List<String> reactorIds, int x,
                                       int y, int elevation, String description, Map<String, String> answers,
                                       Instant openedAt) {

    /** Whether every reactor has answered. */
    public boolean isComplete() {
        return answers != null && answers.keySet().containsAll(reactorIds);
    }
}
