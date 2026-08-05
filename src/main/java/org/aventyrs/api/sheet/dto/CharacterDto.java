package org.aventyrs.api.sheet.dto;

import jakarta.validation.constraints.NotBlank;
import org.aventyrs.core.character.Character.Sexo;

/**
 * A CharacterSheet's embedded Character identity fields. {@code tendencia} is nullable here so a
 * caller can omit it and get core's own default (1) rather than silently binding to 0, same
 * reasoning as {@code CharacterSheetService#normalizeTemporaryEgoPoints}.
 */
public record CharacterDto(
        @NotBlank String name,
        @NotBlank String race,
        Sexo sexo,
        Integer tendencia
) {
}
