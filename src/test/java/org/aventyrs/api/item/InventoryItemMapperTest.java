package org.aventyrs.api.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.aventyrs.api.item.dto.InventoryItemDto;
import org.aventyrs.api.item.dto.PowerStoneDto;
import org.aventyrs.core.item.ItemCategory;
import org.aventyrs.core.item.ItemRarity;
import org.aventyrs.core.item.ItemWeightClass;
import org.aventyrs.core.item.PowerStoneImprovement;
import org.aventyrs.core.item.PowerStoneMasterpiece;
import org.aventyrs.core.item.PowerStoneQuality;
import org.aventyrs.core.item.PowerStoneType;
import org.junit.jupiter.api.Test;

/** Quantities, a Besta's magazine and a stone's Cargas survive the round trip (core 0.1.6.6–0.1.6.7). */
class InventoryItemMapperTest {

    @Test
    void aStackAndALoadedBestaRoundTrip() {
        InventoryItemDto arrows = new InventoryItemDto("FLECHA_BODKIN", "Flecha Bodkin", "", ItemCategory.PROJECTILE,
                ItemRarity.UNCOMMON, ItemWeightClass.LIGHT, 2, 0, 0, 0, 0, 0, null, null, List.of(), null, null, null,
                false, 12, null);
        InventoryItemDto besta = new InventoryItemDto("BESTA_PESADA", "Besta Pesada", "", ItemCategory.CROSSBOW,
                ItemRarity.UNCOMMON, ItemWeightClass.HEAVY, 14, 0, 0, 0, 0, 0, null, null, List.of(), null, null, null,
                false, 1, List.of("VIROTE_BODKIN"));

        List<InventoryItemDto> back = InventoryItemMapper.toDtos(InventoryItemMapper.toEntries(List.of(arrows, besta)));

        assertEquals(12, back.get(0).quantity());
        assertEquals(List.of("VIROTE_BODKIN"), back.get(1).loadedProjectiles());
    }

    @Test
    void aStonesCargasRoundTrip() {
        PowerStoneDto stone = new PowerStoneDto(PowerStoneType.MITRAL_PURO, PowerStoneQuality.JOIA,
                PowerStoneMasterpiece.REFINADA, PowerStoneImprovement.ULTIMA_CARGA, 0, true);
        InventoryItemDto entry = new InventoryItemDto(null, "Mitral Puro (Joia)", "", ItemCategory.POWER_STONE,
                ItemRarity.RARE, ItemWeightClass.LIGHT, 29, 0, 0, 0, 0, 0, null, null, List.of(), stone, null, null,
                false, 1, null);

        PowerStoneDto back = InventoryItemMapper.toDto(InventoryItemMapper.toEntry(entry)).powerStone();

        assertEquals(0, back.remainingCharges());
        assertEquals(true, back.lastChanceUsed());
    }

    @Test
    void anOldEntryReadsAsOneUnitAndAFullStone() {
        InventoryItemEntry old = new InventoryItemEntry(null, "Pedra", "", ItemCategory.POWER_STONE, ItemRarity.RARE,
                ItemWeightClass.LIGHT, 12, 0, 0, 0, 0, 0, null, null, List.of(),
                new PowerStoneEntry(PowerStoneType.MITRAL_PURO, PowerStoneQuality.JOIA, null, null), null, null, false);

        InventoryItemDto dto = InventoryItemMapper.toDto(old);

        assertNull(dto.quantity());
        assertNull(dto.powerStone().remainingCharges());
    }
}
