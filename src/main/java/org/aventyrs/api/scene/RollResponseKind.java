package org.aventyrs.api.scene;

/**
 * How a player answered a roll request.
 *
 * <p>{@link #REACTED} carries no dice and no verdict on purpose. aventyrs-core has no interrupt
 * point — nothing models an attack as a transaction another combatant can interject into
 * mid-resolution, and {@code ReactionsService} reports a per-Rodada maximum that nothing spends —
 * so a reaction is <b>declared and made visible to the table</b>, which then adjudicates it. It is
 * deliberately not a mechanical effect this API pretends to resolve.
 */
public enum RollResponseKind {

    /** The player rolled, and the response carries the dice and the resolved verdict. */
    ROLLED,

    /** The player is doing something else instead — casting, using a Título ability. A declaration. */
    REACTED
}
