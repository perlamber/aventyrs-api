package org.aventyrs.api.sheet.dto;

import org.aventyrs.core.character.Character.Sexo;

public record CharacterResponse(
        String id,
        String name,
        String race,
        Sexo sexo,
        int tendencia
) {
}
