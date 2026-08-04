package org.aventyrs.api.scene.dto;

public record SceneParticipantResponse(
        String characterSheetId,
        int initiativeValue,
        String group,
        GridPositionDto position
) {
}
