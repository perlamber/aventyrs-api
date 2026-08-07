package org.aventyrs.api.scene.dto;

import java.util.UUID;

public record SceneParticipantResponse(
        String characterSheetId,
        int initiativeValue,
        UUID group,
        GridPositionDto position
) {
}
