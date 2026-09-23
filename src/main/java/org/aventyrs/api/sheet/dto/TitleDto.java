package org.aventyrs.api.sheet.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/** See {@code TitleEntry} for why a Título is stored as an identifier plus name lists. */
public record TitleDto(
        @NotBlank String type,
        List<String> specializations,
        List<String> abilities,
        Map<String, String> choices
) {

    /** A Título with no acquisition-time choices. */
    public TitleDto(final String type, final List<String> specializations, final List<String> abilities) {
        this(type, specializations, abilities, null);
    }
}
