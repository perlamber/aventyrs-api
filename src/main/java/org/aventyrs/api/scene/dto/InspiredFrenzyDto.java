package org.aventyrs.api.scene.dto;

import java.util.List;
import java.util.Map;

/**
 * Grito Inspirador's copy of a Gigante Enfurecido's Frenesi, for the clients owning the recipients to
 * rebuild and apply: every bonus by {@code ModifierType} name, every mode by {@code FrenzyMode} name,
 * and the two drawback flags.
 */
public record InspiredFrenzyDto(List<String> recipientCharacterSheetIds, int rounds, Map<String, Integer> bonuses,
                                List<String> modes, boolean concentrationBlocked, boolean scornsDamage) {
}
