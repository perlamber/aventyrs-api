package org.aventyrs.api.sheet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Mirrors {@code org.aventyrs.core.sheet.CharacterSheet#of(Character, Player)}: a new sheet only
 * needs its character and player — every resource/experience/bonus field starts at zero/empty,
 * same as the domain factory. The embedded character's own id is server-generated, same as the
 * sheet's.
 */
public record CharacterSheetCreateRequest(
        @NotNull @Valid CharacterDto character,
        @NotBlank String playerId
) {
}
