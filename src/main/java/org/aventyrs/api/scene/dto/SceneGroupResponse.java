package org.aventyrs.api.scene.dto;

import java.util.List;
import java.util.UUID;

/** Participants in a Scene sharing the same sub-group — see {@code Scene#getAllies} in core. */
public record SceneGroupResponse(
        UUID group,
        List<SceneParticipantResponse> participants
) {
}
