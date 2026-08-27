package org.aventyrs.api.sheet;

import java.util.List;

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
 */
public record TitleEntry(
        String type,
        List<String> specializations,
        List<String> abilities
) {
}
