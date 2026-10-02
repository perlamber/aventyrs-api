package org.aventyrs.api.scene.dto;

import org.aventyrs.core.character.EgoDomain;

/** Broadcast on {@code /topic/scenes/{sceneId}/ego-grants} — see {@link EgoGrantMessage}. */
public record EgoGrantedEvent(EgoDomain domain) {
}
