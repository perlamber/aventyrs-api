package org.aventyrs.api.scene.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.aventyrs.core.item.ItemRarity;

/**
 * {@code itemStoreMaxRarity} mirrors {@code combatScene}'s lifecycle — {@code null} whenever this
 * Scene currently has nowhere to shop, set to a purchasable {@code ItemRarity} once a caller
 * attaches a store. See {@code SceneDocument}'s own javadoc for why only the ceiling is kept.
 */
public record SceneUpdateRequest(
        @NotBlank String name,
        @NotNull List<@Valid SceneParticipantRequest> participants,
        @Min(0) int currentRound,
        int currentIndex,
        boolean combatScene,
        String imageUrl,
        ItemRarity itemStoreMaxRarity
) {
}
