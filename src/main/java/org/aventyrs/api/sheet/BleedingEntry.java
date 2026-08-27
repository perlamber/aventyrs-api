package org.aventyrs.api.sheet;

/**
 * Persisted mirror of {@code org.aventyrs.core.sheet.Bleeding}, which has no public way to be
 * reconstructed from stored state. {@code remainingRounds} is {@code null} for the open-ended
 * Sangramento Maior variant — see that core class's own javadoc.
 */
public record BleedingEntry(int valuePerRound, Integer remainingRounds) {
}
