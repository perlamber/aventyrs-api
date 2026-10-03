package org.aventyrs.api.sheet;

import org.aventyrs.core.subordinate.SubordinateBenefit;

/**
 * A creature trained through Aliado da Natureza (core 0.0.103's {@code skill.empatiaselvagem.TrainedCompanion}): what
 * the trainer calls it and the Cavaleiro/Peão/Torre benefit chosen when it was trained.
 */
public record TrainedCompanionEntry(String name, SubordinateBenefit benefit) {
}
