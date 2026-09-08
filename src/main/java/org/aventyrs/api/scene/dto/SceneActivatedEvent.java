package org.aventyrs.api.scene.dto;

/**
 * Outbound broadcast on {@code /topic/scenes/{originSceneId}/navigate} after a scene-to-scene
 * travel step (see {@code SceneController#move}): the party walked out of {@code originSceneId}
 * along a {@link org.aventyrs.core.scene.Direction}, and the scene named here is the one that just
 * became the table's active scene as a result.
 *
 * <p>Fired only on the travel path, not on a bare {@code PUT /scenes/{id}/active}: activating a
 * scene from the GM console only changes where fresh joins land, whereas travelling is the whole
 * table stepping through a doorway, so every client still standing in {@code originSceneId} follows
 * to {@code sceneId} off this event.
 */
public record SceneActivatedEvent(
        String sceneId,
        String name
) {
}
