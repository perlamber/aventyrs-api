package org.aventyrs.api.scene.dto;

/**
 * One hit landing on a participant — sent to {@code /app/scenes/{sceneId}/damage} by the client that owns the
 * <b>target</b>, since that is where core's {@code DamageService} mitigates it (RD/RA, Escudo) into what was
 * actually deducted. Relayed as-is on {@code /topic/scenes/{sceneId}/damage} so every log can show it, and recorded
 * for the analytics warehouse — a {@link CharacterStatusMessage} only ever carries the running {@code
 * hitPointsSpent}, never who dealt which part of it.
 *
 * <p>Type, element and source kind are Strings for the reason {@link BlessingDto} gives.
 *
 * @param attackerCharacterSheetId who dealt it, or {@code null} for damage with no attacker (a Sangramento tick,
 *                                 the environment)
 * @param rawDamage               the amount before mitigation
 * @param finalDamage             what was actually taken off the target's PV
 * @param ignoreDamageReduction   whether the hit bypassed RD
 * @param damageType              the core {@code DamageType} name, or {@code null}
 * @param elementalType           the core {@code ElementalType} name, or {@code null}
 * @param sourceKind              {@code ATTACK}, {@code ABILITY}, {@code SPELL}, {@code CONDITION}, {@code
 *                                RETALIATION} or {@code OTHER}
 * @param sourceRef               what dealt it within that kind — an ability id, a spell key — or {@code null}
 * @param wasCritical             whether the hit was a critical one, or {@code null} when unknown
 * @param turnNumber              the Rodada it landed in
 * @param hitPointsSpentAfter     the target's {@code hitPointsSpent} once it landed, or {@code null}
 */
public record DamageDealtMessage(String attackerCharacterSheetId, String targetCharacterSheetId, int rawDamage,
                                 int finalDamage, boolean ignoreDamageReduction, String damageType,
                                 String elementalType, String sourceKind, String sourceRef, Boolean wasCritical,
                                 int turnNumber, Integer hitPointsSpentAfter) {
}
