package org.aventyrs.api.sheet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * See {@code FeatEntry} for why a held Talento is stored as its catalog constant's name plus the
 * acquisition-time choice it carries, rather than as a bare name.
 *
 * <p>{@code choices} is nullable — a Talento taken plain simply omits it — and {@code chosenFeat}
 * is null for everything but {@code ExcepcionalidadeFeat}.
 */
public record FeatDto(
        @NotBlank String type,
        List<String> choices,
        @Valid FeatDto chosenFeat
) {
}
