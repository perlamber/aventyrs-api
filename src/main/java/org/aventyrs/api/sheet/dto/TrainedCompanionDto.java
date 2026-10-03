package org.aventyrs.api.sheet.dto;

import org.aventyrs.core.subordinate.SubordinateBenefit;

/** Wire shape of {@code org.aventyrs.api.sheet.TrainedCompanionEntry} — an Aliado da Natureza creature. */
public record TrainedCompanionDto(String name, SubordinateBenefit benefit) {
}
