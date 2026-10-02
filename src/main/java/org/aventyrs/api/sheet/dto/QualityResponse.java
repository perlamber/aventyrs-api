package org.aventyrs.api.sheet.dto;

import java.util.List;

/** See {@code QualityEntry}; {@code levelName} ("Amigável", "Aura de Confiança") is resolved from core for the reader's convenience. */
public record QualityResponse(String type, String qualityClass, String levelName, List<String> choices, String source) {
}
