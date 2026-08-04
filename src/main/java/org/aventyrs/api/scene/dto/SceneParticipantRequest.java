package org.aventyrs.api.scene.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SceneParticipantRequest(
        @NotBlank String characterSheetId,
        int initiativeValue,
        @NotBlank String group,
        @NotNull @Valid GridPositionDto position
) {
}
