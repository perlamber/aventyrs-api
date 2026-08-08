package org.aventyrs.api.scene.dto;

import java.time.Instant;

/** Outbound broadcast on {@code /topic/scenes/{sceneId}/pings} — relayed as-is to every other
 * client watching the scene, with no persistence. */
public record ScenePingEvent(
        GridPositionDto position,
        Instant timestamp
) {
}
