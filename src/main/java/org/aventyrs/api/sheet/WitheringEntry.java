package org.aventyrs.api.sheet;

/**
 * Persisted mirror of {@code org.aventyrs.core.sheet.Withering}, which has no public way to be
 * reconstructed from stored state. {@code remainingRounds} is {@code null} for an open-ended
 * variant, same convention as {@link BleedingEntry}/{@link ManaDrainEntry}. Definhar's own
 * "não cumulativo" rule (core's {@code Withering#isCumulative}) is enforced when the effect is
 * applied, not here — at most one entry is expected in practice, but nothing in this layer
 * assumes that.
 */
public record WitheringEntry(int valuePerRound, Integer remainingRounds) {
}
