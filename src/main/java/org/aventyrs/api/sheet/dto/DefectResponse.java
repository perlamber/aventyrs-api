package org.aventyrs.api.sheet.dto;

import java.util.List;

/** See {@code DefectEntry}; {@code levelName} ("Chato", "Coração de Vidro") is resolved from core for the reader's convenience. */
public record DefectResponse(String type, String severity, String levelName, List<String> choices, boolean fromCreation,
                             String superacao, List<String> superacaoPicks, boolean overcome) {
}
