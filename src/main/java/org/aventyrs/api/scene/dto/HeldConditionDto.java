package org.aventyrs.api.scene.dto;

/**
 * One Condição a participant holds, as its owning client's core sheet reports it — so every other board
 * can mirror it onto its copy of that participant (what the outward Favorecido reads: a Caído foe is
 * easier to hit for everyone). {@code rounds} is {@code null} for open-ended, {@code
 * sourceCharacterSheetId} {@code null} for none.
 */
public record HeldConditionDto(String conditionType, Integer rounds, String sourceCharacterSheetId) {
}
