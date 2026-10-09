package org.aventyrs.api.auth.dto;

import java.time.Instant;
import org.aventyrs.api.player.dto.PlayerResponse;

/**
 * {@code accessToken} goes in {@code Authorization: Bearer <token>} on every REST call and as the
 * {@code Authorization} native header on STOMP SEND/SUBSCRIBE frames (or once on CONNECT).
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        PlayerResponse player
) {
}
