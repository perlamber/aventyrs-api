package org.aventyrs.api.sheet;

import java.util.List;
import java.util.Map;

/**
 * Persisted mirror of a core {@code AventyrTitle} instance (e.g. {@code Santo}) — a plain
 * identifier ({@code type}, e.g. {@code "SANTO"}) plus the Especializações/Habilidades actually
 * held, since {@code AventyrTitle} has no discriminator enum of its own and each concrete Título
 * grants its abilities post-construction rather than fixing them at creation, the same
 * "identifier plus enum constant names" shape {@link RaceEntry} already uses for {@code Race}.
 *
 * <p>{@code specializations} holds the Título's own specialization enum's constant names (e.g.
 * {@code SantoSpecialization.ABENCOADO_PELA_LUZ} → {@code "ABENCOADO_PELA_LUZ"}); {@code
 * abilities} holds every other granted {@code AventyrTitleAbility} constant name, including a
 * held specialization's own gated ability constants — mirroring how the core class itself stores
 * both in one list. Empty, not {@code null}, when nothing is held.
 *
 * <p>{@code choices} holds a Título's acquisition-time picks, keyed by the constant that asks for
 * one and valued by the picked constant's name — e.g. Senhor da Briga's Impacto Elemental element,
 * {@code "IMPACTO_ELEMENTAL" → "FOGO"}. <b>Nullable</b>: every Título stored before 0.0.48 core
 * has no such field, and Spring Data leaves it {@code null} when reading one; read it through
 * {@code CombatantSheetMapper}, which treats {@code null} as "no choices".
 */
public record TitleEntry(
        String type,
        List<String> specializations,
        List<String> abilities,
        Map<String, String> choices
) {

    /** A Título with no acquisition-time choices. */
    public TitleEntry(final String type, final List<String> specializations, final List<String> abilities) {
        this(type, specializations, abilities, null);
    }
}
