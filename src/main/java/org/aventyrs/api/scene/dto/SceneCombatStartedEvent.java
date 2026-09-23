package org.aventyrs.api.scene.dto;

/**
 * Outbound broadcast on {@code /topic/scenes/{sceneId}/combat} after combat is started for a
 * scene (see {@code SceneService#startCombat}, mirroring core 0.0.32's {@code
 * Scene#startCombat()}). {@code combatScene} is {@code true} on the transition into combat, and {@code currentRound} is the Round the scene sits on as
 * combat opens (0, the first Rodada; the counter only starts moving on the next turn wrap now
 * that {@code isCombatScene()} gates it).
 *
 * <p><b>Also carries the end of a combat</b> ({@code SceneService#endCombat}, core 0.0.48's {@code
 * Scene#endCombat()}): the same topic and shape with {@code combatScene} {@code false} and {@code
 * currentRound} back at 0. One record for both transitions keeps a single payload type on the
 * topic; a client reads {@code combatScene} to know which one happened.
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
