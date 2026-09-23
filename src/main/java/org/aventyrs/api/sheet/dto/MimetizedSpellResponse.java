package org.aventyrs.api.sheet.dto;

public record MimetizedSpellResponse(
        String spellName,
        int determinationPointCost,
        boolean selfOnly
) {
}
