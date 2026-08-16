package org.aventyrs.api.sheet;

import org.aventyrs.core.character.EgoDomain;
import org.aventyrs.core.rest.RestType;

/** Persisted mirror of {@code org.aventyrs.core.sheet.PendingEgoRecovery}, which has no public way to be reconstructed from stored state. */
public record PendingEgoRecoveryEntry(EgoDomain domain, int value, RestType minimumRestType) {
}
