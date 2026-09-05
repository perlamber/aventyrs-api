package org.aventyrs.api.item;

import org.aventyrs.core.modifier.ModifierType;

/** Persisted mirror of a core {@code ItemBonus} — one flat, {@link ModifierType}-typed bonus an {@link ItemFavorEntry} grants. */
public record ItemBonusEntry(ModifierType modifierType, int value) {
}
