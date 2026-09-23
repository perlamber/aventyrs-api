package org.aventyrs.api.scene.dto;

import java.util.List;

/**
 * A persisted Título activation, broadcast on {@code /topic/scenes/{sceneId}/abilities} to every
 * client in the Scene — including the one that sent the {@link AbilityActivationMessage} behind it,
 * whose own echo it drops (its log already has the entry, with detail the wire does not carry).
 *
 * <p>Also the shape {@link SceneResponse#abilityHistory()} replays, so a client arriving late still
 * learns what was activated before it connected — the same reason {@link SceneActionEvent} is
 * replayed.

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
public record AbilityActivatedEvent(
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
        int enchantmentRounds,
        TitleEffectsDto effects) {

    /** An activation affecting nobody beyond Blessings and bindings. */
    public AbilityActivatedEvent(String characterSheetId, String titleType, String abilityId, String abilityName,
            int determinationPointsSpent, int hitPointsSpent, int turnNumber, List<BlessingDto> blessings,
            String enchanterCharacterSheetId, List<String> boundCharacterSheetIds, int enchantmentRounds) {
        this(characterSheetId, titleType, abilityId, abilityName, determinationPointsSpent, hitPointsSpent,
                turnNumber, blessings, enchanterCharacterSheetId, boundCharacterSheetIds, enchantmentRounds, null);
    }
}
