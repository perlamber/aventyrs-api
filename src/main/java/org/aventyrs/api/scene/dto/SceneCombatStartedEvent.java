package org.aventyrs.api.scene.dto;

/**
 * Outbound broadcast on {@code /topic/scenes/{sceneId}/combat} after combat is started for a
 * scene (see {@code SceneService#startCombat}, mirroring core 0.0.32's {@code
 * Scene#startCombat()}). {@code combatScene} is always {@code true} here — the event only fires
 * on the transition into combat — and {@code currentRound} is the Round the scene sits on as
 * combat opens (0, the first Rodada; the counter only starts moving on the next turn wrap now
 * that {@code isCombatScene()} gates it).
 *
 * <p>Each client runs its own {@code Scene#startCombat()} off this broadcast, which is where the
 * per-participant start-of-combat Talento Blessings ({@code AnaoFeat#VIGOR_DO_INVERNO}) actually
 * resolve — the live {@code CombatantSheet}s they land on exist only client-side, the same split
 * {@link TurnAdvancedEvent} documents.
 */
public record SceneCombatStartedEvent(
        boolean combatScene,
        int currentRound
) {
}
