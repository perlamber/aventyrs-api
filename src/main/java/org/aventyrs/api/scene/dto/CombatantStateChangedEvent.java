package org.aventyrs.api.scene.dto;

import java.util.List;

/** {@link CombatantStateMessage}, relayed on {@code /topic/scenes/{sceneId}/state}. */
public record CombatantStateChangedEvent(String characterSheetId, String sizeCategory, Integer frenzyRounds,
                                         List<String> frenzyModes, boolean compelled, String riding, boolean ferocious) {

    /**
     * A state message from before core 0.0.67/0.0.69. {@code riding} is the {@code Riding.Kind} the
     * participant rides ({@code MONTARIA}/{@code VEICULO}), or {@code null} on foot; {@code ferocious}
     * whether a Ferocidade de Lacerto is running on them.
     */
    public CombatantStateChangedEvent(String characterSheetId, String sizeCategory, Integer frenzyRounds,
            List<String> frenzyModes, boolean compelled) {
        this(characterSheetId, sizeCategory, frenzyRounds, frenzyModes, compelled, null, false);
    }
}
