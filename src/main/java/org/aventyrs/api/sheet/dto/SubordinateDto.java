package org.aventyrs.api.sheet.dto;

/**
 * One Subordinado a character commands (core 0.0.98) — what core's {@code subordinate.Subordinate} needs to be restored.
 * {@code benefit} is a {@code SubordinateBenefit} name. A sustained one ({@code sustainerId}, {@code trailingRounds})
 * waits on its holder's Concentração; {@code endsAtRest} a {@code RestType} name for one a Descanso ends. {@code id} is the
 * Subordinado's own (core 0.1.5.6), kept so the GM can dismiss it from another client — {@code null} on one saved before.
 */
public record SubordinateDto(String benefit, boolean prodigious, String source, String creatureId,
                             Integer remainingRounds, String sustainerId, Integer trailingRounds, String endsAtRest,
                             String id) {
}
