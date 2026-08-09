package org.aventyrs.api.sheet;

import org.aventyrs.core.modifier.ModifierType;

/** Persisted mirror of {@code org.aventyrs.core.sheet.TemporaryBonus}, which has no public way to be reconstructed from stored state. */
public record TemporaryBonusEntry(ModifierType type, int value, int remainingRounds) {
}
