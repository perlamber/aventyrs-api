package org.aventyrs.api.scene.dto;

import java.util.List;

/** Participants in a Scene sharing the same sub-group — see {@code Scene#getAllies} in core. */
public record SceneGroupResponse(
        String group,
        List<SceneParticipantResponse> participants
) {
}
