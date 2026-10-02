package org.aventyrs.api.sheet.dto;

public record MimetizedSpellResponse(
        String spellName,
        int determinationPointCost,
        boolean selfOnly,
        String requiredForm) {

    /**
     * A mimetized Magia with no Forma requirement. {@code requiredForm} is the {@code
     * org.aventyrs.core.sheet.FormType} name the Magia may only be cast in (core's {@code
     * MimetizedSpell#getRequiredForm()} — Abençoada pelo Conclave's "apenas em Forma Feérica"), or
     * {@code null}.
     */
    public MimetizedSpellResponse(String spellName, int determinationPointCost, boolean selfOnly) {
        this(spellName, determinationPointCost, selfOnly, null);
    }
}
