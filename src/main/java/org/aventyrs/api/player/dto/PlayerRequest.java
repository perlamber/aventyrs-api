package org.aventyrs.api.player.dto;

import jakarta.validation.constraints.NotBlank;

public record PlayerRequest(
        @NotBlank String name,
        @NotBlank String login
) {
}
