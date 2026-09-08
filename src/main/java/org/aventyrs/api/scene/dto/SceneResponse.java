package org.aventyrs.api.scene.dto;

import java.util.List;
import java.util.Map;

import org.aventyrs.core.item.ItemRarity;
import org.aventyrs.core.scene.Direction;
import org.aventyrs.core.scene.TerrainType;

public record SceneResponse(
        String id,
        String name,
        TerrainType terrain,
        List<SceneParticipantResponse> participants,
        int currentRound,
        int currentIndex,
        boolean combatScene,
        boolean active,
        String imageUrl,
        int width,
        int height,
        List<SceneActionEvent> actionHistory,
        List<RollRequestedEvent> rollRequests,
        List<RollRespondedEvent> rollResponses,
        ItemRarity itemStoreMaxRarity,
        Map<Direction, SceneConnectionResponse> connections
) {
}
