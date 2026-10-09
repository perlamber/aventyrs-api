package org.aventyrs.api.scene.dto;

import java.util.List;

/**
 * Wire shape of a {@link ConditionChangeMessage}, broadcast on {@code /topic/scenes/{sceneId}/conditions}
 * to every client watching the scene — the one owning the target applies it; see that message's javadoc
 * for each field.
 */
public record ConditionChangedEvent(String targetCharacterSheetId, String conditionType,
                                    ConditionChangeMessage.Op op, Integer rounds, String sourceCharacterSheetId,
                                    List<ConditionEffectDto> extraEffects, Integer damagePerRound,
                                    Integer escapeDifficulty, Boolean propagates, boolean enchantment) {

    public static ConditionChangedEvent of(final ConditionChangeMessage message) {
        return new ConditionChangedEvent(message.targetCharacterSheetId(), message.conditionType(), message.op(),
                message.rounds(), message.sourceCharacterSheetId(), message.extraEffects(),
                message.damagePerRound(), message.escapeDifficulty(), message.propagates(), message.enchantment());
    }
}
