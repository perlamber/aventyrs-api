package org.aventyrs.api.scene.dto;

/**
 * A Condição an activation cast on someone — Frenesi Assustador's fear — for the client owning the
 * target to apply. {@code enchantment} marks it as an Encantamento cast by the activator.
 */
public record InflictedConditionDto(String targetCharacterSheetId, String conditionType, int rounds,
                                    boolean enchantment) {
}
