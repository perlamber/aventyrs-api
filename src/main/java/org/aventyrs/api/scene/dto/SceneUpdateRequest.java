package org.aventyrs.api.scene.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SceneUpdateRequest(
        @NotBlank String name,
        @NotNull List<@Valid SceneParticipantRequest> participants,
        @Min(0) int currentRound,
        int currentIndex
) {
}
