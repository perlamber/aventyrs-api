package org.aventyrs.api.sheet.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** See {@code DefectEntry}. Nullable lists read as empty; {@code superacao} is {@code null} for a Defeito imposed during play. */
public record DefectDto(@NotBlank String type, @NotBlank String severity, List<String> choices, boolean fromCreation,
                        String superacao, List<String> superacaoPicks, boolean overcome) {
}
