package org.aventyrs.api.scene;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.aventyrs.api.sheet.dto.SubordinateDto;
import org.aventyrs.core.subordinate.SubordinateBenefit;

/**
 * A Rainha's "aumentam sua Iniciativa em +2" in this server's turn order (core 0.1.5.6). The order is owned here and
 * sorted by the stored {@code initiativeValue}, which no client can lift — so the bonus is read off the Subordinados each
 * sheet persists: its own Rainhas (Iniciativa), plus every Prodigioso one an ally of its group and kind commands. Only
 * in a Cena de Combate ("apenas em cenas de estresse"), and never on top of an Iniciativa override, which replaces the
 * whole figure as core's {@code InitiativeEntry#getEffectiveInitiativeValue} does.
 */
final class SubordinateInitiative {

    /** What one participant brings: whether it is a character, and the Subordinados it commands. */
    record Holding(boolean character, List<SubordinateDto> subordinates) {
    }

    private SubordinateInitiative() {
    }

    /** Each participant's Rainha bonus, by id — absent for none. holdings missing an id count it as commanding none. */
    static Map<String, Integer> bonuses(List<SceneParticipantEntry> participants, Map<String, Holding> holdings) {
        Map<String, Integer> bonuses = new HashMap<>();
        for (SceneParticipantEntry entry : participants) {
            Holding own = holdings.get(entry.characterSheetId());
            if (own == null) {
                continue;
            }
            long count = queens(own, false);
            for (SceneParticipantEntry ally : participants) {
                Holding held = holdings.get(ally.characterSheetId());
                if (ally != entry && held != null && Objects.equals(ally.group(), entry.group())
                        && held.character() == own.character()) {
                    count += queens(held, true);
                }
            }
            if (count > 0) {
                bonuses.put(entry.characterSheetId(), (int) count * SubordinateBenefit.INITIATIVE);
            }
        }
        return bonuses;
    }

    /** What a participant sorts by: its override while one holds, else its rolled value plus its Rainha bonus. */
    static int sortValue(SceneParticipantEntry entry, Map<String, Integer> bonuses) {
        return entry.initiativeOverride() != null ? entry.effectiveInitiative()
                : entry.initiativeValue() + bonuses.getOrDefault(entry.characterSheetId(), 0);
    }

    private static long queens(Holding holding, boolean prodigiousOnly) {
        return holding.subordinates() == null ? 0 : holding.subordinates().stream()
                .filter(held -> SubordinateBenefit.RAINHA_INITIATIVE.name().equals(held.benefit()))
                .filter(held -> !prodigiousOnly || held.prodigious())
                .count();
    }
}
