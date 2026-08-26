package org.aventyrs.api.player.dto;

import jakarta.validation.constraints.NotBlank;
import org.aventyrs.api.player.PlayerRole;

/**
 * {@code role} is optional: omitting it means {@link PlayerRole#PLAYER}, so every existing caller
 * keeps working unchanged and only a client that deliberately creates a GM has to say so.
 */
public record PlayerRequest(
        @NotBlank String name,
        @NotBlank String login,
        PlayerRole role
) {

    /**
     * The shape callers that don't care about the role build — every caller that predates the
     * field, and every one creating an ordinary player. Delegates with a null role, which
     * {@code PlayerService} reads as {@link PlayerRole#PLAYER}.
     */
    public PlayerRequest(String name, String login) {
        this(name, login, null);
    }
}
