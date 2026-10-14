package org.aventyrs.api.scene.dto;

import java.util.List;

import org.aventyrs.core.scene.grid.GridPosition;

/**
 * {@code POST /api/scenes/{id}/summons} — one invoked creature (client 0.0.95). The caster's client spawned it and
 * names its id, so its own core sheet and the server's records agree.
 *
 * @param rounds        its Duração, or the N of "Concentração + N" when {@code concentration}; {@code null} lasts
 *                      until dismissed
 * @param position      where its token stands; {@code null} for the free hex nearest the caster
 * @param enhancement   what its summoner's Títulos gave it ({@code NatureInvocationService#enhance}); {@code null} for none
 * @param familiar      the Bruxo's Familiar Maior this token is, with {@code kind} {@code "FAMILIAR_MAIOR"}; {@code null} otherwise
 * @param corpseParticipantId the body Reanimar / Criar Carniçal raises it from (core 0.1.6.24): that participant
 *                      leaves (a PC's sheet is kept) and the creature takes its square, carrying its raise count plus
 *                      one; {@code null} for an invocation raised from nothing
 * @param bodySize      the corpse's Categoria de Tamanho ({@code SizeCategory} name), which the creature takes;
 *                      {@code null} for none
 * @param butcher       Criar Carniçal's Açougueiro
 */
public record SummonCreateRequest(String summonId, String casterCharacterSheetId, String kind,
                                  int conjuradorManaGraduation, List<String> powers, String exclusivityGroup,
                                  Integer rounds, boolean concentration, GridPosition position,
                                  org.aventyrs.core.magic.invocation.SummonEnhancement enhancement,
                                  org.aventyrs.core.title.bruxo.Familiar familiar,
                                  String corpseParticipantId, String bodySize, boolean butcher) {

    /** A summon raised from no corpse — the request shape before core 0.1.6.26. */
    public SummonCreateRequest(final String summonId, final String casterCharacterSheetId, final String kind,
                               final int conjuradorManaGraduation, final List<String> powers,
                               final String exclusivityGroup, final Integer rounds, final boolean concentration,
                               final GridPosition position,
                               final org.aventyrs.core.magic.invocation.SummonEnhancement enhancement,
                               final org.aventyrs.core.title.bruxo.Familiar familiar) {
        this(summonId, casterCharacterSheetId, kind, conjuradorManaGraduation, powers, exclusivityGroup, rounds,
                concentration, position, enhancement, familiar, null, null, false);
    }

    /** An unenhanced invocation — the request shape before core 0.1.2. */
    public SummonCreateRequest(final String summonId, final String casterCharacterSheetId, final String kind,
                               final int conjuradorManaGraduation, final List<String> powers,
                               final String exclusivityGroup, final Integer rounds, final boolean concentration,
                               final GridPosition position) {
        this(summonId, casterCharacterSheetId, kind, conjuradorManaGraduation, powers, exclusivityGroup, rounds,
                concentration, position, null, null);
    }
}
