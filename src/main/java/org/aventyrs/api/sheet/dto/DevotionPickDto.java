package org.aventyrs.api.sheet.dto;

import org.aventyrs.core.character.DevotionTier;

/** Wire shape of {@code org.aventyrs.api.sheet.DevotionPickEntry} — a Talento de Devoção's rung pick. */
public record DevotionPickDto(String talento, DevotionTier rung, String value) {
}
