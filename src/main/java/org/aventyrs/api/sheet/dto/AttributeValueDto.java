package org.aventyrs.api.sheet.dto;

/**
 * Request shape for an {@code AttributeValue} override. Every field is nullable so a caller can
 * supply just one component (e.g. {@code racialBonus}) and let the rest fall back to core's own
 * defaults (base 1, racialBonus/variable 0) — see {@code CharacterSheetService#normalizeAttributes}.
 */
public record AttributeValueDto(Integer base, Integer racialBonus, Integer variable) {
}
