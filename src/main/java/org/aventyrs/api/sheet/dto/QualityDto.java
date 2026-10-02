package org.aventyrs.api.sheet.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** See {@code QualityEntry}. A nullable choices list reads as empty. */
public record QualityDto(@NotBlank String type, @NotBlank String qualityClass, List<String> choices,
                         @NotBlank String source) {
}
