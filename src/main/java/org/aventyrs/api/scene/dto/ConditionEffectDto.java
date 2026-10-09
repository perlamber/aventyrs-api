package org.aventyrs.api.scene.dto;

/**
 * One magnitude an inflicting source carries on a Condição — aventyrs-core's {@code
 * ConditionType.ConditionEffect} on the wire: a {@code ModifierType} name, its value, and the {@code
 * Range} band it is scoped to ({@code null} for always). A Veneno's "-1 Multiplicador de PV" is {@code
 * ("LIFE_MULTIPLIER", -1, null)}.
 */
public record ConditionEffectDto(String modifierType, int value, String within) {
}
