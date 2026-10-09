package org.aventyrs.api.scene.dto;

import java.util.List;

/**
 * A participant's live state the rest of the table must draw — sent to {@code
 * /app/scenes/{sceneId}/state} and relayed, never persisted: the state lives on the owning
 * client's core sheet.
 *
 * @param sizeCategory   the effective Categoria de Tamanho name (a Titã Enlouquecido grows), or {@code null}
 * @param frenzyRounds   Rodadas left in the participant's own Frenesi, or {@code null} when none runs
 * @param frenzyModes    its {@code FrenzyMode} names
 * @param compelled      whether the participant must attack the nearest creature
 */
public record CombatantStateMessage(String characterSheetId, String sizeCategory, Integer frenzyRounds,
                                    List<String> frenzyModes, boolean compelled, String riding, boolean ferocious,
                                    boolean concentrating, List<HeldConditionDto> conditions) {

    /**
     * A state message from before core 0.1.5. {@code conditions} are the Condições the participant holds
     * (core {@code CombatantSheet#getHeldConditions}), so every board can mirror them; {@code null} on an
     * older frame means "not reported", never "none".
     */
    public CombatantStateMessage(String characterSheetId, String sizeCategory, Integer frenzyRounds,
            List<String> frenzyModes, boolean compelled, String riding, boolean ferocious, boolean concentrating) {
        this(characterSheetId, sizeCategory, frenzyRounds, frenzyModes, compelled, riding, ferocious, concentrating,
                null);
    }

    /**
     * A state message from before core 0.1.1. {@code concentrating} is whether the participant holds a Concentração
     * (core {@code CombatantSheet#isConcentrating}); its turning false is how the table learns it was lost.
     */
    public CombatantStateMessage(String characterSheetId, String sizeCategory, Integer frenzyRounds,
            List<String> frenzyModes, boolean compelled, String riding, boolean ferocious) {
        this(characterSheetId, sizeCategory, frenzyRounds, frenzyModes, compelled, riding, ferocious, false, null);
    }

    /**
     * A state message from before core 0.0.67/0.0.69. {@code riding} is the {@code Riding.Kind} the
     * participant rides ({@code MONTARIA}/{@code VEICULO}), or {@code null} on foot; {@code ferocious}
     * whether a Ferocidade de Lacerto is running on them.
     */
    public CombatantStateMessage(String characterSheetId, String sizeCategory, Integer frenzyRounds,
            List<String> frenzyModes, boolean compelled) {
        this(characterSheetId, sizeCategory, frenzyRounds, frenzyModes, compelled, null, false, false, null);
    }
}
