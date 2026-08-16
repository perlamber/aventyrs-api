package org.aventyrs.api.sheet;

/**
 * Persisted mirror of {@code org.aventyrs.core.sheet.ManaDrain}, which has no public way to be
 * reconstructed from stored state. {@code remainingRounds} is {@code null} for the open-ended
 * ManaPurge Maior variant — see that core class's own javadoc.
 */
public record ManaDrainEntry(int valuePerRound, Integer remainingRounds) {
}
