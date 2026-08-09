package org.aventyrs.api.scene.dto;

import java.util.List;

public record SceneResponse(
        String id,
        String name,
        List<SceneParticipantResponse> participants,
        int currentRound,
        int currentIndex
) {
}
