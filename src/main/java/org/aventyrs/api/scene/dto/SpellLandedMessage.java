package org.aventyrs.api.scene.dto;

import java.util.List;

/**
 * Sent to {@code /app/scenes/{sceneId}/spells} when a cast Magia lands on a target whose real sheet another client owns
 * (client 0.0.94). Relayed as-is on {@code /topic/scenes/{sceneId}/spells}, never persisted: the owning client rebuilds
 * the effect from core with the dice already thrown here and applies it to the real sheet, then broadcasts its status.
 *
 * @param spellKey      the Magia as {@code TREE:CONSTANT}
 * @param alternate     whether its Efeito Alternativo was cast
 * @param hostile       whether the target is hostile to the caster (Nova Rejuvenescedora halves)
 * @param healingBonus  the caster's healing bonus for the cast
 * @param chainDie      the Corrente's die when it fired on this target, else {@code null}
 * @param castDice      the Conjuração roll's faces — what a casting critical is read from
 * @param criticalDice  dice a casting critical throws (Potencializar), in order
 * @param fixedHealing  PV healed outright instead of the Magia's effect — an invoked creature's healing the Magia
 *                      brought (the Anciente's Benção Compartilhada); {@code null} for an ordinary landing
 * @param chosenAttribute the caster's "Força ou Destreza" pick ({@code AttributeDomain} name) for a Magia that asks one
 *                      — Ogrificar (client 0.0.99); {@code null} otherwise
 */
public record SpellLandedMessage(String casterCharacterSheetId, String spellKey, boolean alternate,
                                 String targetCharacterSheetId, boolean hostile, int healingBonus, Integer chainDie,
                                 List<Integer> castDice, List<Integer> criticalDice, Integer fixedHealing,
                                 String chosenAttribute) {

    /** A landing with no Atributo pick. */
    public SpellLandedMessage(String casterCharacterSheetId, String spellKey, boolean alternate,
                              String targetCharacterSheetId, boolean hostile, int healingBonus, Integer chainDie,
                              List<Integer> castDice, List<Integer> criticalDice, Integer fixedHealing) {
        this(casterCharacterSheetId, spellKey, alternate, targetCharacterSheetId, hostile, healingBonus, chainDie,
                castDice, criticalDice, fixedHealing, null);
    }

    /** An ordinary landing — no fixed healing. */
    public SpellLandedMessage(String casterCharacterSheetId, String spellKey, boolean alternate,
                              String targetCharacterSheetId, boolean hostile, int healingBonus, Integer chainDie,
                              List<Integer> castDice, List<Integer> criticalDice) {
        this(casterCharacterSheetId, spellKey, alternate, targetCharacterSheetId, hostile, healingBonus, chainDie,
                castDice, criticalDice, null);
    }
}
