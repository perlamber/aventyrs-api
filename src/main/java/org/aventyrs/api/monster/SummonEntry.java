package org.aventyrs.api.monster;

import java.util.List;

/**
 * What an invoked creature is (client 0.0.95) — persisted on its {@link MonsterSheetDocument} in place of a blueprint.
 * Core rebuilds it with {@code NatureSummon#restore}: {@code kind} a {@code NatureSummonKind}, the Conjurador's
 * Graduação in Domínio do Mana, and the {@code LacertoPower}s rolled at the cast. {@code casterCharacterSheetId} is who
 * invoked it; the document's {@code playerId} is the caster's player, who controls it (table ruling, 2026-10-01).
 * Since core 0.1.2 it also carries the summoner's {@code SummonEnhancement} (Bruxo) and, for a Familiar Maior's token
 * ({@link #FAMILIAR_KIND}), the {@code Familiar} it is — both {@code null} otherwise.
 *
 * <p>Since core 0.1.6.26 {@code kind} may also name an undead: a {@code UndeadSummonKind} (ZUMBI, CARNICAL, ABANTESMA —
 * {@code bodySize} the corpse's Categoria, {@code butcher} Criar Carniçal's Açougueiro), or a generated Carrasco
 * Moribundo ({@link #CARRASCO_PREFIX} + a {@code MonsterCategory}), built from its stock blueprint.
 */
public record SummonEntry(String kind, int conjuradorManaGraduation, List<String> powers,
                          String casterCharacterSheetId,
                          org.aventyrs.core.magic.invocation.SummonEnhancement enhancement,
                          org.aventyrs.core.title.bruxo.Familiar familiar,
                          String bodySize, boolean butcher, boolean uncontrolled) {

    /** An undead descriptor without the control flag — every one but Festim dos Mortos' excess. */
    public SummonEntry(final String kind, final int conjuradorManaGraduation, final List<String> powers,
                       final String casterCharacterSheetId,
                       final org.aventyrs.core.magic.invocation.SummonEnhancement enhancement,
                       final org.aventyrs.core.title.bruxo.Familiar familiar, final String bodySize,
                       final boolean butcher) {
        this(kind, conjuradorManaGraduation, powers, casterCharacterSheetId, enhancement, familiar, bodySize, butcher,
                false);
    }

    /** This descriptor for a creature that obeys nobody (core 0.1.6.26) — what every client reads to treat it as a foe. */
    public SummonEntry asUncontrolled() {
        return new SummonEntry(kind, conjuradorManaGraduation, powers, casterCharacterSheetId, enhancement, familiar,
                bodySize, butcher, true);
    }

    /** A Bruxo's Familiar Maior token's {@code kind} (core 0.1.2) — rebuilt from {@link #familiar}, not a NatureSummon. */
    public static final String FAMILIAR_KIND = "FAMILIAR_MAIOR";

    /** A monster-invoked Carrasco Moribundo's {@code kind}: this, then its Categoria — {@code CARRASCO_DEVIANTE}. */
    public static final String CARRASCO_PREFIX = "CARRASCO_";

    /** An unenhanced Aliados da Natureza creature — every summon stored before core 0.1.2. */
    public SummonEntry(final String kind, final int conjuradorManaGraduation, final List<String> powers,
                       final String casterCharacterSheetId) {
        this(kind, conjuradorManaGraduation, powers, casterCharacterSheetId, null, null, null, false, false);
    }

    /** A nature summon or a Familiar — the shape before core 0.1.6.26. */
    public SummonEntry(final String kind, final int conjuradorManaGraduation, final List<String> powers,
                       final String casterCharacterSheetId,
                       final org.aventyrs.core.magic.invocation.SummonEnhancement enhancement,
                       final org.aventyrs.core.title.bruxo.Familiar familiar) {
        this(kind, conjuradorManaGraduation, powers, casterCharacterSheetId, enhancement, familiar, null, false, false);
    }

    /** Whether kind names a REANIMAR creature ({@code UndeadSummonKind}). */
    public boolean isUndeadKind() {
        return kind != null && java.util.Arrays.stream(org.aventyrs.core.monster.summon.UndeadSummonKind.values())
                .anyMatch(undead -> undead.name().equals(kind));
    }

    /** Whether kind names a generated Carrasco Moribundo. */
    public boolean isCarrasco() {
        return kind != null && kind.startsWith(CARRASCO_PREFIX);
    }

    /**
     * The core stat block this descriptor rebuilds: the Familiar Maior's token when it names one, a REANIMAR creature,
     * otherwise the Aliados da Natureza creature with whatever its summoner's Títulos gave it ({@code enhancement},
     * {@code null} on a summon stored before 0.1.2). Not for a Carrasco — see {@link #spawn}.
     */
    public org.aventyrs.core.monster.SummonedMonsterTemplate toTemplate() {
        if (familiar != null) {
            return new org.aventyrs.core.title.bruxo.FamiliarTemplate(familiar);
        }
        if (isUndeadKind()) {
            return org.aventyrs.core.monster.summon.UndeadSummonKind.restore(kind, conjuradorManaGraduation, bodySize,
                    butcher);
        }
        return org.aventyrs.core.monster.summon.NatureSummon.restore(kind, conjuradorManaGraduation, powers, enhancement);
    }

    /** The core sheet this descriptor rebuilds — a Carrasco from its stock blueprint, anything else from {@link #toTemplate}. */
    public org.aventyrs.core.monster.MonsterSheet spawn(final org.aventyrs.core.sheet.Player gm) {
        if (isCarrasco()) {
            return org.aventyrs.core.monster.summon.CarrascoMorimbundo.stock(org.aventyrs.core.monster.MonsterCategory
                    .valueOf(kind.substring(CARRASCO_PREFIX.length()))).spawn(gm);
        }
        return toTemplate().spawn(gm);
    }
}
