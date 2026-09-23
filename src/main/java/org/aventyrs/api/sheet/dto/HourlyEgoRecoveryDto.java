package org.aventyrs.api.sheet.dto;

import org.aventyrs.core.character.EgoDomain;

/** Wire shape of {@code HourlyEgoRecoveryEntry}. */
public record HourlyEgoRecoveryDto(EgoDomain domain, int points, int hoursPerPoint, int bankedHours) {
}
