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
        List<AbilityActivatedEvent> abilityHistory,
        List<RollRequestedEvent> rollRequests,
        List<RollRespondedEvent> rollResponses,
        ItemRarity itemStoreMaxRarity,
        Map<Direction, SceneConnectionResponse> connections,
        List<GridPositionDto> difficultTerrain,
        List<org.aventyrs.api.scene.SceneSummonEntry> summons,
        List<org.aventyrs.api.scene.SceneSummonSpawnerEntry> summonSpawners,
        List<TerrainCellDto> terrainCells,
        ReactionWindowEvent reactionWindow,
        /** Whose Rodada 0.0 action it is (core 0.1.7.15), or {@code null}. */
        String preInitiativeActor,
        /** Who still waits for a Rodada 0.0 action, in order — empty when none. */
        List<String> preInitiativeQueue
) {
}
