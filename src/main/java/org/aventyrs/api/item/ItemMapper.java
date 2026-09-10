package org.aventyrs.api.item;

import java.util.List;
import org.aventyrs.api.item.dto.ItemBonusDto;
import org.aventyrs.api.item.dto.ItemFavorDto;
import org.aventyrs.api.item.dto.ItemImprovementDto;
import org.aventyrs.api.item.dto.ItemMasterpieceDto;
import org.aventyrs.api.item.dto.ItemRequest;
import org.aventyrs.api.item.dto.ItemResponse;
import org.aventyrs.api.item.dto.PowerStoneDto;

/** The {@code ItemRequest}/{@code ItemDocument}/{@code ItemResponse} marshaling, factored out of {@link ItemService}. */
final class ItemMapper {

    private ItemMapper() {
    }

    static void copyOnto(ItemDocument document, ItemRequest request) {
        document.setTemplateName(request.templateName());
        document.setName(request.name());
        document.setDescription(request.description());
        document.setCategory(request.category());
        document.setRarity(request.rarity());
        document.setWeightClass(request.weightClass());
        document.setPrice(request.price());
        document.setPhysicalDefenseBonus(request.physicalDefenseBonus());
        document.setMagicDefenseBonus(request.magicDefenseBonus());
        document.setHardness(request.hardness());
        document.setDamageTaken(request.damageTaken());
        document.setCastingBonus(request.castingBonus());
        document.setFavor(toFavorEntry(request.favor()));
        document.setMasterpiece(toMasterpieceEntry(request.masterpiece()));
        document.setImprovements(toImprovementEntries(request.improvements()));
        document.setPowerStone(toPowerStoneEntry(request.powerStone()));
        document.setRegaliaGrade(request.regaliaGrade());
        document.setActiveAbilityName(request.activeAbilityName());
        document.setProducedByCharacterId(request.producedByCharacterId());
        document.setDonatedByAventyr(request.donatedByAventyr());
    }

    static ItemResponse toResponse(ItemDocument document) {
        return new ItemResponse(
                document.getId(),
                document.getTemplateName(),
                document.getName(),
                document.getDescription(),
                document.getCategory(),
                document.getRarity(),
                document.getWeightClass(),
                document.getPrice(),
                document.getPhysicalDefenseBonus(),
                document.getMagicDefenseBonus(),
                document.getHardness(),
                document.getDamageTaken(),
                document.getCastingBonus(),
                toFavorDto(document.getFavor()),
                toMasterpieceDto(document.getMasterpiece()),
                toImprovementDtos(document.getImprovements()),
                toPowerStoneDto(document.getPowerStone()),
                document.getRegaliaGrade(),
                document.getActiveAbilityName(),
                document.getProducedByCharacterId(),
                document.isDonatedByAventyr(),
                document.getImprovementEffectSceneId(),
                document.getImprovementEffectLastActiveRound());
    }

    static ItemFavorEntry toFavorEntry(ItemFavorDto favor) {
        if (favor == null) {
            return null;
        }
        List<ItemBonusEntry> bonuses = favor.bonuses() == null ? List.of() : favor.bonuses().stream()
                .map(bonus -> new ItemBonusEntry(bonus.modifierType(), bonus.value()))
                .toList();
        return new ItemFavorEntry(favor.description(), favor.requiredAttributeDomain(),
                favor.requiredAttributeValue(), bonuses, favor.additionalEffects());
    }

    static ItemFavorDto toFavorDto(ItemFavorEntry favor) {
        if (favor == null) {
            return null;
        }
        List<ItemBonusDto> bonuses = favor.bonuses() == null ? List.of() : favor.bonuses().stream()
                .map(bonus -> new ItemBonusDto(bonus.modifierType(), bonus.value()))
                .toList();
        return new ItemFavorDto(favor.description(), favor.requiredAttributeDomain(),
                favor.requiredAttributeValue(), bonuses, favor.additionalEffects());
    }

    static ItemMasterpieceEntry toMasterpieceEntry(ItemMasterpieceDto masterpiece) {
        return masterpiece == null ? null
                : new ItemMasterpieceEntry(masterpiece.definition(), masterpiece.selectedDefense(),
                        masterpiece.selectedActionBonus());
    }

    static ItemMasterpieceDto toMasterpieceDto(ItemMasterpieceEntry masterpiece) {
        return masterpiece == null ? null
                : new ItemMasterpieceDto(masterpiece.definition(), masterpiece.selectedDefense(),
                        masterpiece.selectedActionBonus());
    }

    static List<ItemImprovementEntry> toImprovementEntries(List<ItemImprovementDto> improvements) {
        return improvements == null ? List.of() : improvements.stream()
                .map(improvement -> new ItemImprovementEntry(improvement.definition(),
                        improvement.selectedDefense(), improvement.selectedElementalType()))
                .toList();
    }

    static List<ItemImprovementDto> toImprovementDtos(List<ItemImprovementEntry> improvements) {
        return improvements == null ? List.of() : improvements.stream()
                .map(improvement -> new ItemImprovementDto(improvement.definition(),
                        improvement.selectedDefense(), improvement.selectedElementalType()))
                .toList();
    }

    static PowerStoneEntry toPowerStoneEntry(PowerStoneDto powerStone) {
        return powerStone == null ? null
                : new PowerStoneEntry(powerStone.type(), powerStone.quality(), powerStone.masterpiece(),
                        powerStone.improvement());
    }

    static PowerStoneDto toPowerStoneDto(PowerStoneEntry powerStone) {
        return powerStone == null ? null
                : new PowerStoneDto(powerStone.type(), powerStone.quality(), powerStone.masterpiece(),
                        powerStone.improvement());
    }
}
