package org.aventyrs.api.sheet;

import org.aventyrs.core.character.DevotionTier;

/**
 * One pick a Talento de Devoção's rung asked for (core 0.0.86's {@code feat.DevotionPick}): the Talento's {@code
 * DevotoFeat} name, the rung, and the picked value as the client encodes it — an enum constant's name, or a
 * Habilidade/Especialização as {@code SKILL:KIND:NAME}. Stored verbatim; the client owns the value's encoding.
 */
public record DevotionPickEntry(String talento, DevotionTier rung, String value) {
}
