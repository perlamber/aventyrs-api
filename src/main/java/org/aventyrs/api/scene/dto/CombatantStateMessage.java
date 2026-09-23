package org.aventyrs.api.scene.dto;

import java.util.List;

/**
 * A participant's live state the rest of the table must draw — sent to {@code
 * /app/scenes/{sceneId}/state} and relayed, never persisted, exactly as {@link HiddenStatusMessage}
 * is: the state lives on the owning client's core sheet.
 *
 * @param sizeCategory   the effective Categoria de Tamanho name (a Titã Enlouquecido grows), or {@code null}
 * @param frenzyRounds   Rodadas left in the participant's own Frenesi, or {@code null} when none runs
 * @param frenzyModes    its {@code FrenzyMode} names
 * @param compelled      whether the participant must attack the nearest creature
 */
public record CombatantStateMessage(String characterSheetId, String sizeCategory, Integer frenzyRounds,
                                    List<String> frenzyModes, boolean compelled) {
}
