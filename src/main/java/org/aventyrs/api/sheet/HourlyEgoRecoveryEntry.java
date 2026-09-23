package org.aventyrs.api.sheet;

import org.aventyrs.core.character.EgoDomain;

/**
 * One debt of temporary Ego points owed back by the hour — core's {@code sheet.HourlyEgoRecovery},
 * persisted: the Autocontrole a Gigante Enfurecido's Frenesi spent comes back "1 a cada 2 horas".
 * Numbers are boxed so a document written before a field existed still reads.
 */
public record HourlyEgoRecoveryEntry(EgoDomain domain, Integer points, Integer hoursPerPoint, Integer bankedHours) {
}
