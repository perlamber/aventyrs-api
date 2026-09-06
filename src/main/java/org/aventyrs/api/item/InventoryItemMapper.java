package org.aventyrs.api.item;

import java.util.List;
import org.aventyrs.api.item.dto.InventoryItemDto;

/**
 * {@code InventoryItemDto} &harr; {@link InventoryItemEntry} marshaling — the embedded-inventory
 * counterpart of {@link ItemMapper}, which does the same for a standalone {@link ItemDocument}.
 * Reuses {@link ItemMapper}'s favor/masterpiece/improvement/power-stone helpers so the two stay
 * in lockstep.
 *
 * <p>A {@code null} list means "sheet persisted before structured inventory existed" and reads as
 * empty, the same normalisation the sheet documents' Efeito lists already get.
 */
public final class InventoryItemMapper {

    private InventoryItemMapper() {
    }

    public static List<InventoryItemEntry> toEntries(List<InventoryItemDto> dtos) {
        return dtos == null ? List.of() : dtos.stream().map(InventoryItemMapper::toEntry).toList();
    }

    public static List<InventoryItemDto> toDtos(List<InventoryItemEntry> entries) {
        return entries == null ? List.of() : entries.stream().map(InventoryItemMapper::toDto).toList();
    }

    static InventoryItemEntry toEntry(InventoryItemDto dto) {
        return new InventoryItemEntry(
                dto.templateName(),
                dto.name(),
                dto.description(),
                dto.category(),
                dto.rarity(),
                dto.weightClass(),
                dto.price(),
                dto.physicalDefenseBonus(),
                dto.magicDefenseBonus(),
                dto.hardness(),
                dto.damageTaken(),
                dto.castingBonus(),
                ItemMapper.toFavorEntry(dto.favor()),
                ItemMapper.toMasterpieceEntry(dto.masterpiece()),
                ItemMapper.toImprovementEntries(dto.improvements()),
                ItemMapper.toPowerStoneEntry(dto.powerStone()),
                dto.regaliaGrade(),
                dto.activeAbilityName(),
                dto.donatedByAventyr());
    }

    static InventoryItemDto toDto(InventoryItemEntry entry) {
        return new InventoryItemDto(
                entry.templateName(),
                entry.name(),
                entry.description(),
                entry.category(),
                entry.rarity(),
                entry.weightClass(),
                entry.price(),
                entry.physicalDefenseBonus(),
                entry.magicDefenseBonus(),
                entry.hardness(),
                entry.damageTaken(),
                entry.castingBonus(),
                ItemMapper.toFavorDto(entry.favor()),
                ItemMapper.toMasterpieceDto(entry.masterpiece()),
                ItemMapper.toImprovementDtos(entry.improvements()),
                ItemMapper.toPowerStoneDto(entry.powerStone()),
                entry.regaliaGrade(),
                entry.activeAbilityName(),
                entry.donatedByAventyr());
    }
}
