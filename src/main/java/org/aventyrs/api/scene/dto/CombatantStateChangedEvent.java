package org.aventyrs.api.scene.dto;

import java.util.List;

/** {@link CombatantStateMessage}, relayed on {@code /topic/scenes/{sceneId}/state}. */
public record CombatantStateChangedEvent(String characterSheetId, String sizeCategory, Integer frenzyRounds,
                                         List<String> frenzyModes, boolean compelled) {
}
