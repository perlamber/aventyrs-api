package org.aventyrs.api.scene.dto;

import java.util.List;

/** {@link CombatantStateMessage}, relayed on {@code /topic/scenes/{sceneId}/state}. */
public record CombatantStateChangedEvent(String characterSheetId, String sizeCategory, Integer frenzyRounds,
                                         List<String> frenzyModes, boolean compelled, String riding, boolean ferocious,
                                         boolean concentrating) {

    /**
     * A state message from before core 0.1.1. {@code concentrating} is whether the participant holds a Concentração
     * (core {@code CombatantSheet#isConcentrating}); its turning false is how the table learns it was lost.
     */
    public CombatantStateChangedEvent(String characterSheetId, String sizeCategory, Integer frenzyRounds,
            List<String> frenzyModes, boolean compelled, String riding, boolean ferocious) {
        this(characterSheetId, sizeCategory, frenzyRounds, frenzyModes, compelled, riding, ferocious, false);
    }

    /**
     * A state message from before core 0.0.67/0.0.69. {@code riding} is the {@code Riding.Kind} the
     * participant rides ({@code MONTARIA}/{@code VEICULO}), or {@code null} on foot; {@code ferocious}
     * whether a Ferocidade de Lacerto is running on them.
     */
    public CombatantStateChangedEvent(String characterSheetId, String sizeCategory, Integer frenzyRounds,
            List<String> frenzyModes, boolean compelled) {
        this(characterSheetId, sizeCategory, frenzyRounds, frenzyModes, compelled, null, false, false);
    }
}
