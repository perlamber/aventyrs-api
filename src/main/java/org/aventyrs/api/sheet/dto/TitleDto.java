package org.aventyrs.api.sheet.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/** See {@code TitleEntry} for why a Título is stored as an identifier plus name lists. */
public record TitleDto(
        @NotBlank String type,
        List<String> specializations,
        List<String> abilities
) {
}
