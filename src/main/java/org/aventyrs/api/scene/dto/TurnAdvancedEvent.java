package org.aventyrs.api.scene.dto;

import java.util.List;

/**
 * Outbound broadcast on {@code /topic/scenes/{sceneId}/turn} after a turn advance is accepted —
 * the persisted cursor {@code SceneService#advanceTurn} moved to, plus whose Turn it now is.
 *
 * <p>Clients rebuild their own {@code org.aventyrs.core.scene.Scene} around these values rather
 * than being told what happened to each participant: the lifecycle work (a {@code finishTurn()}
 * on whoever just ended, a {@code startTurn(int)} on whoever's beginning) can only run where the
 * real {@code CombatantSheet}s live, which is each client, never here.
 *
 * <p>{@code rotation} is the turn order as this server now stores it, the CharacterSheet ids of everyone in the
 * rotation in order. A Rodada wrap re-sorts it here; a client re-sorting on its own would weigh Iniciativa bonuses
 * only it can see. So each client adopts this order instead, and {@code currentIndex} means the same combatant
 * on every screen.
 */
public record TurnAdvancedEvent(
        String characterSheetId,
        int currentRound,
        int currentIndex,
        List<String> rotation,
        /**
         * Whether this hands out a Rodada 0.0 action (Paranoia Saudável, core 0.1.7.15) rather than a Turn: the cursor
         * stays before the first Turn and characterSheetId acts once, ignoring Iniciativa. {@code null}/false for a
         * real Turn.
         */
        Boolean preInitiative
) {

    /** A real Turn — every advance before core 0.1.7.15. */
    public TurnAdvancedEvent(String characterSheetId, int currentRound, int currentIndex, List<String> rotation) {
        this(characterSheetId, currentRound, currentIndex, rotation, null);
    }
}
