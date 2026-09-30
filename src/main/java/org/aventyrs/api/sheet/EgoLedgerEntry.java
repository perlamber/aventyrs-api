package org.aventyrs.api.sheet;

import org.aventyrs.core.character.EgoDomain;

import java.util.Map;

/**
 * The Ego pool state beyond the temporary spent-count ({@code temporaryEgoPoints}) — what core 0.0.76's pools
 * need to rebuild a sheet exactly: the permanent points spent, the extra temporary points held above the ceiling,
 * and how much of a past-5 Ego's overflow was already received (core's {@code
 * CombatantSheet#getEgoOverflowReceived}), and the face each Ego rolled on its setback table when it reached zero
 * (core 0.0.82, {@code ego.EgoSetback}; 0 = none). Each map is per {@link EgoDomain}; an absent domain is 0. Stored as
 * the client computed it — this API doesn't run the rules.
 */
public record EgoLedgerEntry(Map<EgoDomain, Integer> permanentSpent, Map<EgoDomain, Integer> extras,
                             Map<EgoDomain, Integer> overflowReceived, Map<EgoDomain, Integer> setbacks) {

    /** A ledger from before core 0.0.82's Ego-at-zero setbacks — none rolled. */
    public EgoLedgerEntry(Map<EgoDomain, Integer> permanentSpent, Map<EgoDomain, Integer> extras,
                          Map<EgoDomain, Integer> overflowReceived) {
        this(permanentSpent, extras, overflowReceived, null);
    }
}
