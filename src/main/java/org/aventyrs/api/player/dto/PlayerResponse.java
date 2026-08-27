package org.aventyrs.api.player.dto;

import org.aventyrs.api.player.PlayerRole;

/** {@code role} is never null on the wire — see {@code PlayerService#roleOf}. */
public record PlayerResponse(
        String id,
        String name,
        String login,
        PlayerRole role
) {
}
