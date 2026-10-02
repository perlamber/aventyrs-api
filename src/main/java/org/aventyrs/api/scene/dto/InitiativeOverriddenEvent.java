package org.aventyrs.api.scene.dto;

/** Broadcast on {@code /topic/scenes/{sceneId}/initiative} once an {@link InitiativeOverrideMessage} is stored. */
public record InitiativeOverriddenEvent(String characterSheetId, int value, Integer rodadas) {
}
