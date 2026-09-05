package org.aventyrs.api.scene;

/**
 * Which of core's two {@code org.aventyrs.core.skill.AttackSource} implementers delivered a
 * recorded action — {@code null} on {@link SceneActionEntry} when the action wasn't an attack, or
 * the caller didn't say. Only the Weapon-vs-Spell category is persisted, not which weapon/spell:
 * {@code AssassinoFeat#SAQUE_RELAMPAGO} — the one clause that reads {@code
 * org.aventyrs.core.sheet.CombatantAction#attackSource()} — only needs to tell a ranged weapon
 * apart from a ranged Magia, both of which otherwise share {@code
 * org.aventyrs.core.skill.SkillType#ATAQUE_A_DISTANCIA}. There is no persisted equivalent of the
 * specific {@code Weapon}/{@code Spell} instance for the same reason nothing else here reaches for
 * one: a {@code Weapon} in play is a stateful, separately persisted {@code
 * org.aventyrs.api.item.ItemDocument}, and a {@code Spell} is a stateless catalog constant — this
 * log entry has no need for either identity, only the category.
 */
public enum AttackSourceKind {
    WEAPON, SPELL
}
