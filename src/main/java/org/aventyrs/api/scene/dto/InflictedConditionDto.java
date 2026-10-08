package org.aventyrs.api.scene.dto;

/**
 * A Condição an activation cast on someone — Frenesi Assustador's fear — for the client owning the
 * target to apply. {@code enchantment} marks it as an Encantamento cast by the activator;
 * {@code sourceCharacterSheetId} is its origin (core 0.1.5), {@code null} when the activator is.
 */
public record InflictedConditionDto(String targetCharacterSheetId, String conditionType, int rounds,
                                    boolean enchantment, String sourceCharacterSheetId) {

    /** An inflicted Condição from before core 0.1.5 — its origin is the activator. */
    public InflictedConditionDto(String targetCharacterSheetId, String conditionType, int rounds,
                                 boolean enchantment) {
        this(targetCharacterSheetId, conditionType, rounds, enchantment, null);
    }
}
