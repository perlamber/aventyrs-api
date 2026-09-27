package org.aventyrs.api.sheet.dto;

import jakarta.validation.constraints.NotBlank;

/** See {@code MimetizedSpellEntry} for why a mimetized Magia is stored as its catalog name plus
 * the mimicry-specific terms, and for why the optional activation-time/duration overrides aren't
 * carried here. */
public record MimetizedSpellDto(
        @NotBlank String spellName,
        int determinationPointCost,
        boolean selfOnly,
        String requiredForm) {

    /**
     * A mimetized Magia with no Forma requirement. {@code requiredForm} is the {@code
     * org.aventyrs.core.sheet.FormType} name the Magia may only be cast in (core's {@code
     * MimetizedSpell#getRequiredForm()} — Abençoada pelo Conclave's "apenas em Forma Feérica"), or
     * {@code null}.
     */
    public MimetizedSpellDto(String spellName, int determinationPointCost, boolean selfOnly) {
        this(spellName, determinationPointCost, selfOnly, null);
    }
}
