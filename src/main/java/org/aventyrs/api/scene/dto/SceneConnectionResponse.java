package org.aventyrs.api.scene.dto;

/**
 * One resolved neighbour on a {@link SceneResponse#connections()} map: the scene that lies in some
 * {@link org.aventyrs.core.scene.Direction} from the one being read.
 *
 * <p>The connection itself is stored as just the neighbour's id (see {@code SceneDocument}); this is
 * that id resolved against the scenes collection on the read path, so a client rendering "travel
 * north to …" has the neighbour's name and whether it's already the active scene without a second
 * round-trip. {@code name} is {@code null} and {@code active} {@code false} when the id no longer
 * resolves — a dangling link left by a deleted neighbour, still surfaced so the gap is visible
 * rather than silently dropped.
 */
public record SceneConnectionResponse(
        String id,
        String name,
        boolean active
) {
}
