package org.aventyrs.api.scene.dto;

/**
 * Outbound broadcast on {@code /topic/scenes/{sceneId}/turn} after a turn advance is accepted —
 * the persisted cursor {@code SceneService#advanceTurn} moved to, plus whose Turn it now is.
 *
 * <p>Clients rebuild their own {@code org.aventyrs.core.scene.Scene} around these values rather
 * than being told what happened to each participant: the lifecycle work (a {@code finishTurn()}
 * on whoever just ended, a {@code startTurn(int)} on whoever's beginning) can only run where the
 * real {@code CombatantSheet}s live, which is each client, never here.
 */
public record TurnAdvancedEvent(
        String characterSheetId,
        int currentRound,
        int currentIndex
) {
}
