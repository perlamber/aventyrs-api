package org.aventyrs.api.scene.dto;

import java.util.List;

/**
 * Inbound STOMP payload for {@code /app/scenes/{sceneId}/conditions} — a Condição put on, or taken off,
 * a participant some other client owns (core 0.1.5): an Agarrar landing, an escape, a foe's Desacordado.
 * Resolved client-side by aventyrs-core and taken at face value here; the owning client applies it to
 * its own core sheet and confirms through its next {@code /state} frame. Relayed, never persisted —
 * the Condição lives on that core sheet, the same stance {@link CombatantStateMessage} takes.
 *
 * @param targetCharacterSheetId  whose sheet changes
 * @param conditionType           the {@code ConditionType} name
 * @param op                      {@code APPLY} or {@code REMOVE}
 * @param rounds                  its Duração in Rodadas, {@code null} for open-ended (Caído, Agarrado)
 * @param sourceCharacterSheetId  its origin — the captor, the fear's source — or {@code null}
 * @param extraEffects            the inflicting source's own magnitudes, or empty
 * @param damagePerRound          a Veneno's continuous Dano Natural ({@code Poisoning}), or {@code null}
 * @param escapeDifficulty        an Imobilizado's preset Furtividade GD ({@code Immobilization}), or {@code null}
 * @param propagates              whether a Doença spreads ({@code Disease}), or {@code null}
 * @param enchantment             whether it is an Encantamento cast by the source (the fear of Frenesi Assustador)
 */
public record ConditionChangeMessage(String targetCharacterSheetId, String conditionType, Op op, Integer rounds,
                                     String sourceCharacterSheetId, List<ConditionEffectDto> extraEffects,
                                     Integer damagePerRound, Integer escapeDifficulty, Boolean propagates,
                                     boolean enchantment) {

    /** Putting a Condição on, or taking it off. */
    public enum Op { APPLY, REMOVE }

    public ConditionChangeMessage {
        extraEffects = extraEffects == null ? List.of() : List.copyOf(extraEffects);
    }
}
