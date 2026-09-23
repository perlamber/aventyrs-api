package org.aventyrs.api.sheet;

/**
 * Persisted mirror of a core {@code MimetizedSpell} — a Magia granted by a mimetizing Talento
 * rather than learned, stored as the catalog Magia's own {@code Spell#getName()} plus the
 * mimicry-specific terms, the same "identifier plus the delta" shape {@link TitleEntry} uses for
 * a held Título.
 *
 * <p>Deliberately does not mirror {@code MimetizedSpell#activationTimeOverride}/{@code
 * durationOverride} — both are optional per-mimicry overrides with no persisted representation
 * yet; a reader of this entry sees the catalog Magia's own activation time/duration instead.
 * That's a display simplification, not a correctness gap in core: nothing here executes a cast.
 */
public record MimetizedSpellEntry(
        String spellName,
        int determinationPointCost,
        boolean selfOnly
) {
}
