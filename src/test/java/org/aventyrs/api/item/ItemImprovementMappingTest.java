package org.aventyrs.api.item;

import java.util.List;

import org.aventyrs.api.item.dto.ItemImprovementDto;
import org.aventyrs.core.scene.TerrainType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A fitted Aprimoramento's creation-time choices survive the trip to storage and back (core 0.1.6.11). */
class ItemImprovementMappingTest {

    @Test
    void camufladasTerrainRoundTrips() {
        ItemImprovementDto camuflada = new ItemImprovementDto("CAMUFLADA", null, null, TerrainType.FOREST);

        List<ItemImprovementDto> back = ItemMapper.toImprovementDtos(ItemMapper.toImprovementEntries(List.of(camuflada)));

        assertEquals(List.of(camuflada), back);
    }

    @Test
    void anOlderAprimoramentoHasNoTerrain() {
        ItemImprovementEntry older = new ItemImprovementEntry("OCULTA", null, null);

        assertNull(older.selectedTerrain());
    }
}
