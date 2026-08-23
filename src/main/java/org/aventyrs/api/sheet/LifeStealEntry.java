package org.aventyrs.api.sheet;

/**
 * Persisted mirror of {@code org.aventyrs.core.sheet.LifeSteal}, which has no public way to be
 * reconstructed from stored state. Unlike {@link BleedingEntry}/{@link ManaDrainEntry}/{@link
 * WitheringEntry}, {@code value} isn't per-round — Roubo de Vida only matters the moment its
 * holder deals damage — but it's stored the same way regardless.
 */
public record LifeStealEntry(int value, Integer remainingRounds) {
}
