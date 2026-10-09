package org.aventyrs.api.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.aventyrs.api.sheet.dto.SubordinateDto;
import org.junit.jupiter.api.Test;

/** A Rainha's "+2 Iniciativa" in the server's order (core 0.1.5.6). */
class SubordinateInitiativeTest {

    private static final UUID HEROES = UUID.randomUUID();
    private static final UUID FOES = UUID.randomUUID();

    private static SceneParticipantEntry entry(String id, int initiative, UUID group) {
        return new SceneParticipantEntry(id, initiative, group, null, 0, null);
    }

    private static SubordinateDto rainha(boolean prodigious) {
        return new SubordinateDto("RAINHA_INITIATIVE", prodigious, "Mestre", null, null, null, null, null, null);
    }

    @Test
    void aRainhaLiftsItsCommanderAndAProdigiosoOneLiftsSameKindAllies() {
        SceneParticipantEntry queen = entry("queen", 10, HEROES);
        SceneParticipantEntry ally = entry("ally", 10, HEROES);
        SceneParticipantEntry pet = entry("pet", 10, HEROES);
        SceneParticipantEntry foe = entry("foe", 10, FOES);
        Map<String, SubordinateInitiative.Holding> holdings = Map.of(
                "queen", new SubordinateInitiative.Holding(true, List.of(rainha(true), rainha(false))),
                "ally", new SubordinateInitiative.Holding(true, List.of()),
                "pet", new SubordinateInitiative.Holding(false, List.of()),
                "foe", new SubordinateInitiative.Holding(false, null));

        Map<String, Integer> bonuses = SubordinateInitiative.bonuses(List.of(queen, ally, pet, foe), holdings);

        assertEquals(Map.of("queen", 4, "ally", 2), bonuses, "stacked on its commander; a monster or foe gets none");
        assertEquals(14, SubordinateInitiative.sortValue(queen, bonuses));
        assertEquals(10, SubordinateInitiative.sortValue(foe, bonuses));
    }

    @Test
    void anOverrideReplacesTheWholeFigure() {
        SceneParticipantEntry queen = entry("queen", 10, HEROES)
                .withInitiativeOverride(new SceneInitiativeOverrideEntry(3, null, false));
        Map<String, Integer> bonuses = SubordinateInitiative.bonuses(List.of(queen),
                Map.of("queen", new SubordinateInitiative.Holding(true, List.of(rainha(false)))));

        assertEquals(3, SubordinateInitiative.sortValue(queen, bonuses));
    }
}
