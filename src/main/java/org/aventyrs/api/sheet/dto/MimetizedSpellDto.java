package org.aventyrs.api.sheet.dto;

import jakarta.validation.constraints.NotBlank;

/** See {@code MimetizedSpellEntry} for why a mimetized Magia is stored as its catalog name plus
 * the mimicry-specific terms, and for why the optional activation-time/duration overrides aren't
 * carried here. */
public record MimetizedSpellDto(
        @NotBlank String spellName,
        int determinationPointCost,
        boolean selfOnly
) {
}
