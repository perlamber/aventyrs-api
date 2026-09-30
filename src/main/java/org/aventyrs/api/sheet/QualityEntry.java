package org.aventyrs.api.sheet;

import java.util.List;

/**
 * Persisted mirror of core's {@code HeldQuality} — {@code type} the {@code Quality#name()}, {@code qualityClass}
 * the {@code QualityClass} ({@code MENOR}/{@code MAIOR}), {@code source} the {@code QualitySource}, and {@code
 * choices} as strings the way {@link DefectEntry} stores them.
 */
public record QualityEntry(String type, String qualityClass, List<String> choices, String source) {
}
