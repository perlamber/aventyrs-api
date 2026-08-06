package org.aventyrs.api.sheet.dto;

/**
 * Request shape for an {@code EgoValue} override. Every field is nullable so a caller can supply
 * just one component and let the rest fall back to core's own defaults (base 2, variable 0) —
 * see {@code CharacterSheetService#normalizeEgos}.
 */
public record EgoValueDto(Integer base, Integer variable) {
}
