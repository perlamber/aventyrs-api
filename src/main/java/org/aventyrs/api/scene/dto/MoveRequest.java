package org.aventyrs.api.scene.dto;

import java.util.Set;
import java.util.UUID;

/**
 * Body of {@code POST /scenes/{id}/move/{direction}}: which participant groups travel with the
 * party to the neighbouring scene. Every participant whose {@code group} is in {@code carryGroups}
 * is moved out of the origin scene and into the destination (server-assigned position); groups
 * left out stay behind in the origin scene.
 *
 * <p>An absent body, or a {@code null}/empty {@code carryGroups}, carries nobody — the step just
 * activates the neighbour, the pre-carry-over behaviour.
 */
public record MoveRequest(
        Set<UUID> carryGroups
) {
}
