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
 * @param criticalResult           the core {@code CriticalResult} name the Efeitos Críticos were inflicted at, or
 *                                 {@code null} when none fired
 * @param criticalEffects          the core {@code CriticalEffectType} names that fired on this target, in chain
 *                                 order — the owner rebuilds and applies them to the real sheet; {@code null} for none
 * @param bypassedReductions       the core {@code BypassedReduction} names the hit ignores (a Benção Vulcana weapon's
 *                                 RD, its Corrente's RE and RM — core 0.1.6.9), or {@code null} for none
 * @param additionalElement        the core {@code ElementalType} the hit deals on top of its own type (a Benção
 *                                 Elemental weapon), or {@code null}
 * @param sanctity                 the core {@code DamageSanctity} name the hit carries (Profano, Sagrado, Umbral —
 *                                 core 0.1.6.10), or {@code null}
 * @param attackSkill              the attack's Perícia ({@code SkillType} name) when this frame also tells the owner
 *                                 its sheet was attacked — hit or miss — so its worn Aprimoramentos answer (Benção
 *                                 Ymiriana, Espinhosa, Rigidez Refletora — core 0.1.6.11); else {@code null}
 * @param primaryTarget            whether the owner's sheet was the attack's primary target, or {@code null}
 * @param projectile               the core {@code ProjectileItem} name the attack fired at it, or {@code null}
 * @param weaponDamage             on a frame sent back to an attacker (sourceKind {@code RETALIATION}): what the
 *                                 weapon they attacked with takes, or {@code null}
 * @param projectileDestroyed      on such a frame: whether the projectiles they fired were destroyed, or {@code null}
 */
public record AttackHitMessage(String attackerCharacterSheetId, String targetCharacterSheetId, int rawDamage,
                               boolean ignoreDamageReduction, boolean halved, String damageType,
                               String elementalType, String sourceKind, String sourceRef, Boolean wasCritical,
                               String criticalResult, java.util.List<String> criticalEffects,
                               java.util.List<String> bypassedReductions, String additionalElement,
                               String sanctity, String attackSkill, Boolean primaryTarget, String projectile,
                               Integer weaponDamage, Boolean projectileDestroyed) {

    /** Without the hit's sanctity and the attack notice — every hit before core 0.1.6.11. */
    public AttackHitMessage(String attackerCharacterSheetId, String targetCharacterSheetId, int rawDamage,
            boolean ignoreDamageReduction, boolean halved, String damageType, String elementalType,
            String sourceKind, String sourceRef, Boolean wasCritical, String criticalResult,
            java.util.List<String> criticalEffects, java.util.List<String> bypassedReductions,
            String additionalElement) {
        this(attackerCharacterSheetId, targetCharacterSheetId, rawDamage, ignoreDamageReduction, halved, damageType,
                elementalType, sourceKind, sourceRef, wasCritical, criticalResult, criticalEffects, bypassedReductions,
                additionalElement, null, null, null, null, null, null);
    }

    /** Without the weapon's mitigation bypass and added element — every hit before core 0.1.6.9. */
    public AttackHitMessage(String attackerCharacterSheetId, String targetCharacterSheetId, int rawDamage,
            boolean ignoreDamageReduction, boolean halved, String damageType, String elementalType,
            String sourceKind, String sourceRef, Boolean wasCritical, String criticalResult,
            java.util.List<String> criticalEffects) {
        this(attackerCharacterSheetId, targetCharacterSheetId, rawDamage, ignoreDamageReduction, halved, damageType,
                elementalType, sourceKind, sourceRef, wasCritical, criticalResult, criticalEffects, null, null,
                null, null, null, null, null, null);
    }

    /** Without the Efeitos Críticos — a hit that carries damage only. */
    public AttackHitMessage(String attackerCharacterSheetId, String targetCharacterSheetId, int rawDamage,
            boolean ignoreDamageReduction, boolean halved, String damageType, String elementalType,
            String sourceKind, String sourceRef, Boolean wasCritical) {
        this(attackerCharacterSheetId, targetCharacterSheetId, rawDamage, ignoreDamageReduction, halved, damageType,
                elementalType, sourceKind, sourceRef, wasCritical, null, null, null, null,
                null, null, null, null, null, null);
    }

    /** Whether this hit carries anything for the owner to apply: damage, or an Efeito Crítico. */
    public boolean carriesAnything() {
        return rawDamage > 0 || criticalEffects != null && !criticalEffects.isEmpty() || attackSkill != null
                || weaponDamage != null && weaponDamage > 0 || Boolean.TRUE.equals(projectileDestroyed)
                || SOLVE_VIDAS.equals(sourceKind);
    }

    /**
     * The sourceKind of a Maldição: Solve-Vidas reaping (core 0.1.6.11): the wearer's client asks the fallen victim's
     * owner to kill its real sheet — a frame with no damage of its own — and the owner answers with {@link
     * #SOLVE_VIDAS_RECOVERY}, whose rawDamage is the PV the wearer recovers (the victim's Vigor).
     */
    public static final String SOLVE_VIDAS = "SOLVE_VIDAS";

    /** See {@link #SOLVE_VIDAS}. */
    public static final String SOLVE_VIDAS_RECOVERY = "SOLVE_VIDAS_RECOVERY";
}
