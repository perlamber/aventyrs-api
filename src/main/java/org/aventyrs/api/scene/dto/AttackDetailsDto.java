package org.aventyrs.api.scene.dto;

import java.util.List;

/**
 * What the attacking client's aventyrs-core resolved about an attack beyond the roll itself — the
 * weapon or Magia it was delivered with, its Margem Crítica, and the Efeitos Críticos / Corrente de
 * Efeitos it set off (core's {@code combat.DeliveredAttackResult}). Carried on {@link
 * RecordActionMessage}/{@link SceneActionEvent} and persisted on {@code SceneActionEntry} so the
 * combat log, and the analytics warehouse reading it, know <em>how</em> a hit landed, not only that it
 * did.
 *
 * <p>Every part is optional: {@code null} as a whole from a client that predates it, and any single
 * field {@code null} when the client didn't know it. Numbers are boxed and enum-like values are
 * Strings, for the reasons {@link TargetEffectDto} and {@link BlessingDto} give — this rides inside a
 * persisted entry, and a client on a newer core may name a constant this server has never heard of.
 *
 * @param weaponItemId       the wielded weapon's {@code ItemDocument} id, {@code null} for a Magia or an unarmed blow
 * @param weaponName         the weapon's printed name
 * @param weaponCategory     its core {@code ItemCategory} name ({@code LIGHT_BLADE}, {@code BOW}, …)
 * @param spellKey           the Magia as {@code TREE:CONSTANT}, as {@link SpellLandedMessage} names it
 * @param criticalMargin     the Margem Crítica Menor the roll was read against — the weapon's own margin
 *                           after every widening ({@code SkillRoll#getCriticalResult(int, int)})
 * @param requiredTotal      the total the attack had to reach
 * @param criticalEffectTriggered whether an Efeito Crítico fired
 * @param criticalEffects    the {@code CriticalEffectType} names that fired
 * @param effectChainTriggered whether the Corrente de Efeitos fired
 * @param chainedEffects     what the Corrente set off, by name
 * @param additionalTargetCharacterSheetIds every target beyond the primary one an area or chained attack hit
 */
public record AttackDetailsDto(String weaponItemId, String weaponName, String weaponCategory, String spellKey,
                               Integer criticalMargin, Integer requiredTotal, Boolean criticalEffectTriggered,
                               List<String> criticalEffects, Boolean effectChainTriggered,
                               List<String> chainedEffects, List<String> additionalTargetCharacterSheetIds) {
}
