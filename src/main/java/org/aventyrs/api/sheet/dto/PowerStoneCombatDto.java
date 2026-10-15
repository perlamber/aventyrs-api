package org.aventyrs.api.sheet.dto;

/**
 * One piece of a character's combat-scoped Pedra do Poder state (core 0.1.6.7), as the owner's client reports it on
 * the {@code /status} frame: a running Vinculação or an item's Resfriamento. Items have no persisted identity, so each
 * names its item by position — {@code stoneIndex} in the sheet's {@code inventory}, {@code hostIndex} in the
 * character's {@code equipment} — exactly as those lists are stored. Cleared when the combat ends (the client then
 * sends an empty list). This API stores the shape and returns it; core decides everything.
 *
 * @param kind            {@code BINDING}, {@code STONE_COOLDOWN} or {@code HOST_COOLDOWN}
 * @param stoneIndex      the stone's inventory position ({@code BINDING}, {@code STONE_COOLDOWN})
 * @param hostIndex       the host's equipment position ({@code BINDING}, {@code HOST_COOLDOWN})
 * @param remainingRounds Rodadas left of the binding or the Resfriamento
 * @param durationRounds  the binding's full Duração ({@code BINDING} only)
 * @param cooldownRounds  the Resfriamento the binding ends into ({@code BINDING} only)
 */
public record PowerStoneCombatDto(String kind, Integer stoneIndex, Integer hostIndex, Integer remainingRounds,
                                  Integer durationRounds, Integer cooldownRounds) {
}
