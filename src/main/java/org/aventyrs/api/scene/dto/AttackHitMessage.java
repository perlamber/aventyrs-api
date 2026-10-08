package org.aventyrs.api.scene.dto;

/**
 * A hit that landed on a sheet <b>another</b> client owns — sent to {@code /app/scenes/{sceneId}/hits} by the client
 * that rolled it, and relayed unchanged on {@code /topic/scenes/{sceneId}/hits}. The attacking client only holds a
 * stand-in for that sheet, with none of its RD, immunities or Escudo, so it sends the hit unmitigated; the owning
 * client runs it through core against the real sheet, then reports what it actually took as a {@link
 * DamageDealtMessage}. Not recorded for analytics here — that {@code /damage} frame is the one record of the hit.
 *
 * <p>Type, element and source kind are Strings for the reason {@link BlessingDto} gives.
 *
 * @param attackerCharacterSheetId who dealt it
 * @param targetCharacterSheetId   whose real sheet takes it
 * @param rawDamage                the amount before mitigation
 * @param ignoreDamageReduction    whether the hit bypasses RD
 * @param halved                   whether the hit lands as Meio-Dano (an additional target, a provoking Aura)
 * @param damageType               the core {@code DamageType} name, or {@code null}
 * @param elementalType            the core {@code ElementalType} name, or {@code null}
 * @param sourceKind               as {@link DamageDealtMessage#sourceKind()}
 * @param sourceRef                as {@link DamageDealtMessage#sourceRef()}
 * @param wasCritical              whether the attack was a critical hit, or {@code null} when unknown
 */
public record AttackHitMessage(String attackerCharacterSheetId, String targetCharacterSheetId, int rawDamage,
                               boolean ignoreDamageReduction, boolean halved, String damageType,
                               String elementalType, String sourceKind, String sourceRef, Boolean wasCritical) {
}
