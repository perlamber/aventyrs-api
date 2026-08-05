package org.aventyrs.api.sheet;

/** Persisted mirror of core's {@code AttributeValue}: base + racial bonus + variable components. */
public record AttributeValueEntry(int base, int racialBonus, int variable) {
}
