package org.aventyrs.api.sheet.dto;

import org.aventyrs.core.character.DevotionTier;

/** The Narrador's change of a character's devotion tier (core 0.0.86) — {@code null} is no devotion. */
public record DevotionTierRequest(DevotionTier tier) {
}
