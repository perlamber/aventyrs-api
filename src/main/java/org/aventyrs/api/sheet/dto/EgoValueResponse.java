package org.aventyrs.api.sheet.dto;

/** {@code total} mirrors core's {@code EgoValue#getTotal()} so clients don't need to sum it themselves. */
public record EgoValueResponse(int base, int variable, int total) {
}
