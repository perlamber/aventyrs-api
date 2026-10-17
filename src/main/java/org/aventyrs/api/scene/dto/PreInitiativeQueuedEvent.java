package org.aventyrs.api.scene.dto;

import java.util.List;

/**
 * Who waits for a Rodada 0.0 action, in order (core 0.1.7.15) — broadcast on {@code
 * /topic/scenes/{sceneId}/pre-initiative} after each claim. Each gets theirs from a turn advance whose {@code
 * TurnAdvancedEvent#preInitiative} is true.
 */
public record PreInitiativeQueuedEvent(String claimedBy, List<String> queue) {
}
