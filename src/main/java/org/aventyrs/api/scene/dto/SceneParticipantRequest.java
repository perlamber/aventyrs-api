package org.aventyrs.api.scene.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SceneParticipantRequest(
        @NotBlank String characterSheetId,
        int initiativeValue,
        @NotNull UUID group,
        @NotNull @Valid GridPositionDto position,
        @Min(0) int joinedAtRound
) {
}
