package org.aventyrs.api.scene.dto;

import java.util.List;

/**
 * A Habilidade de Título one client just activated — sent to {@code
 * /app/scenes/{sceneId}/abilities}.
 *
 * <p>Already paid for and already resolved by the time it arrives: this server runs no rules engine
 * (see {@link org.aventyrs.api.scene.SceneService}), so it persists and relays what the activating
 * client's own aventyrs-core decided, the same trust {@link CharacterStatusMessage} places in a
 * reported damage tier.
 *
 * <p>{@code abilityId} is the catalog constant name and {@code abilityName} the printed name the
 * sender rendered. Both travel because they answer different questions: the id is what a receiving
 * client matches against its own catalog, while the name is what it can still show for a Título it
 * does not know — a table where one player runs a newer build stays legible either way.
 *
 * <p>{@code blessings} is what the activation granted for <em>other</em> characters to receive. A
 * {@code SELF_AND_ALLIES} clause cannot be resolved by whoever activated it: the allies' real
 * sheets live in their own clients, so each recipient's client grants the bonus to the sheets it
 * owns when this arrives. Empty for the many traits that grant nothing.

 *
 * <p>{@code enchanterCharacterSheetId}/{@code boundCharacterSheetIds}/{@code enchantmentRounds}
 * carry an Aura's catch. An Encantamento like Orgulho Elduriano lives on <b>the sheet it binds</b>,
 * and that sheet's real owner is another client — the activator only ever holds a stand-in for it,
 * so a binding applied locally would be invisible to exactly the client that drives the bound
 * combatant's attacks. Relaying who was caught lets each client apply the compulsion to the sheets
 * it actually owns.
 *
 * <p>An Aura also catches foes who merely <em>walk into</em> it, long after the activation. Those
 * arrive as this same shape with the costs zeroed, naming the trait that caught them: the message
 * means "this trait bound these combatants", of which the activation itself is the first instance.
 */
public record AbilityActivationMessage(
        String characterSheetId,
        String titleType,
        String abilityId,
        String abilityName,
        int determinationPointsSpent,
        int hitPointsSpent,
        int turnNumber,
        List<BlessingDto> blessings,
        String enchanterCharacterSheetId,
        List<String> boundCharacterSheetIds,
        int enchantmentRounds) {
}
