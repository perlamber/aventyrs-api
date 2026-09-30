package org.aventyrs.api.sheet;

import org.aventyrs.core.character.EgoDomain;

import java.util.Map;

/**
 * The Ego pool state beyond the temporary spent-count ({@code temporaryEgoPoints}) — what core 0.0.76's pools
 * need to rebuild a sheet exactly: the permanent points spent, the extra temporary points held above the ceiling,
 * and how much of a past-5 Ego's overflow was already received (core's {@code
 * CombatantSheet#getEgoOverflowReceived}). Each map is per {@link EgoDomain}; an absent domain is 0. Stored as
 * the client computed it — this API doesn't run the rules.
 */
public record EgoLedgerEntry(Map<EgoDomain, Integer> permanentSpent, Map<EgoDomain, Integer> extras,
                             Map<EgoDomain, Integer> overflowReceived) {
}
