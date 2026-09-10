package org.aventyrs.api.scene;

/**
 * What a {@link SceneRollRequestEntry} is asking for.
 *
 * <p>The distinction is how the answering client resolves it, not what it looks like: an {@link
 * #ATTACK} goes through core's {@code AttackReceiver}, which derives its threshold from the
 * attack's Grau de Dificuldade plus a flat bonus and eases it by the defender's own difficulty
 * reduction; a {@link #CHECK} is a plain Perícia roll stated against a GD via {@code
 * SkillRoll.against}. Both end in a verdict, by different arithmetic.
 */
public enum RollRequestKind {

    /**
     * A foe is attacking. In this ruleset the player always rolls, so the attack presents a Grau
     * de Dificuldade and a flat bonus and the <em>defender</em> rolls Esquiva e Aparar against it.
     */
    ATTACK,

    /** The Narrador asked for a Perícia roll against a Grau de Dificuldade they set. */
    CHECK
}
