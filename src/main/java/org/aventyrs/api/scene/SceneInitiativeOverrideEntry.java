package org.aventyrs.api.scene;

/**
 * An Iniciativa a participant's Ego point put in place of its rolled one (core 0.0.79's {@code
 * CombatantSheet#overrideInitiative}), persisted because this server owns the turn order. {@code rodadas} is how
 * many more Rodadas it governs ({@code null}: the rest of the Cena); {@code started} whether a Rodada boundary has
 * passed since it was set — the first boundary starts its first Rodada, each later one ends one. Advanced by
 * {@code SceneService#advanceTurn}'s wrap right before it re-sorts, exactly as core's {@code Scene} does.
 */
public record SceneInitiativeOverrideEntry(int value, Integer rodadas, boolean started) {

    /** This override one Rodada boundary on — {@code null} once it lapses. */
    SceneInitiativeOverrideEntry advanced() {
        if (!started) {
            return new SceneInitiativeOverrideEntry(value, rodadas, true);
        }
        if (rodadas == null) {
            return this;
        }
        return rodadas <= 1 ? null : new SceneInitiativeOverrideEntry(value, rodadas - 1, true);
    }
}
