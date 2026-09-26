package org.aventyrs.api.monster.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.aventyrs.api.item.dto.InventoryItemDto;

/**
 * A new monster: its blueprint (the Mestre's choices — core derives everything else), the GM who
 * authored it, its token and its loot. Refused with {@code 400 MONSTER_RULES_VIOLATED} when core's
 * {@code MonsterRules#validate} finds the blueprint illegal.
 */
public record MonsterSheetCreateRequest(
        @NotNull @Valid MonsterBlueprintDto blueprint,
        @NotBlank String playerId,
        String tokenImageUrl,
        List<@Valid InventoryItemDto> inventory
) {
}
