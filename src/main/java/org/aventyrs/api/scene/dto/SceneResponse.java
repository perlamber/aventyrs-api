package org.aventyrs.api.scene.dto;

import java.util.List;

import org.aventyrs.core.scene.TerrainType;

public record SceneResponse(
        String id,
        String name,
        TerrainType terrain,
        List<SceneParticipantResponse> participants,
        int currentRound,
        int currentIndex
) {
}
