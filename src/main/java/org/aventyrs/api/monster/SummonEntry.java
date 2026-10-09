package org.aventyrs.api.monster;

import java.util.List;

/**
 * What an invoked creature is (client 0.0.95) — persisted on its {@link MonsterSheetDocument} in place of a blueprint.
 * Core rebuilds it with {@code NatureSummon#restore}: {@code kind} a {@code NatureSummonKind}, the Conjurador's
 * Graduação in Domínio do Mana, and the {@code LacertoPower}s rolled at the cast. {@code casterCharacterSheetId} is who
 * invoked it; the document's {@code playerId} is the caster's player, who controls it (table ruling, 2026-10-01).
 * Since core 0.1.2 it also carries the summoner's {@code SummonEnhancement} (Bruxo) and, for a Familiar Maior's token
 * ({@link #FAMILIAR_KIND}), the {@code Familiar} it is — both {@code null} otherwise.
 */
public record SummonEntry(String kind, int conjuradorManaGraduation, List<String> powers,
                          String casterCharacterSheetId,
                          org.aventyrs.core.magic.invocation.SummonEnhancement enhancement,
                          org.aventyrs.core.title.bruxo.Familiar familiar) {

    /** A Bruxo's Familiar Maior token's {@code kind} (core 0.1.2) — rebuilt from {@link #familiar}, not a NatureSummon. */
    public static final String FAMILIAR_KIND = "FAMILIAR_MAIOR";

    /** An unenhanced Aliados da Natureza creature — every summon stored before core 0.1.2. */
    public SummonEntry(final String kind, final int conjuradorManaGraduation, final List<String> powers,
                       final String casterCharacterSheetId) {
        this(kind, conjuradorManaGraduation, powers, casterCharacterSheetId, null, null);
    }

    /**
     * The core stat block this descriptor rebuilds: the Familiar Maior's token when it names one, otherwise the
     * Aliados da Natureza creature with whatever its summoner's Títulos gave it ({@code enhancement}, {@code null} on a
     * summon stored before 0.1.2).
     */
    public org.aventyrs.core.monster.SummonedMonsterTemplate toTemplate() {
        if (familiar != null) {
            return new org.aventyrs.core.title.bruxo.FamiliarTemplate(familiar);
        }
        return org.aventyrs.core.monster.summon.NatureSummon.restore(kind, conjuradorManaGraduation, powers, enhancement);
    }
}
