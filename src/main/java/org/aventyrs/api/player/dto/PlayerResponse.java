package org.aventyrs.api.player.dto;

public record PlayerResponse(
        String id,
        String name,
        String login
) {
}
