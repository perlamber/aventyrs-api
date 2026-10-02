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
 */
public record SummonCreateRequest(String summonId, String casterCharacterSheetId, String kind,
                                  int conjuradorManaGraduation, List<String> powers, String exclusivityGroup,
                                  Integer rounds, boolean concentration, GridPosition position) {
}
