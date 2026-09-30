package org.aventyrs.api.sheet.dto;

import jakarta.validation.constraints.Min;
import org.aventyrs.core.character.EgoDomain;

import java.util.Map;

/** Wire shape of {@code EgoLedgerEntry}. Every map is filled for all four Egos on a response. */
public record EgoLedgerDto(Map<EgoDomain, @Min(0) Integer> permanentSpent, Map<EgoDomain, @Min(0) Integer> extras,
                           Map<EgoDomain, @Min(0) Integer> overflowReceived) {
}
