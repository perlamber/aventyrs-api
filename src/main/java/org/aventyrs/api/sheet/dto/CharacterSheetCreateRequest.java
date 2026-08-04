package org.aventyrs.api.sheet.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Mirrors {@code org.aventyrs.core.sheet.CharacterSheet#of(Character, Player)}: a new sheet only
 * needs its character and player references — every resource/experience/bonus field starts at
 * zero/empty, same as the domain factory.
 */
public record CharacterSheetCreateRequest(
        @NotBlank String characterId,
        @NotBlank String playerId
) {
}
