package org.aventyrs.api.sheet.dto;

/** {@code total} mirrors core's {@code AttributeValue#getTotal()} so clients don't need to sum it themselves. */
public record AttributeValueResponse(int base, int racialBonus, int variable, int total) {
}
