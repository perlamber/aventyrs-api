package org.aventyrs.api.campaign.dto;

import java.time.Instant;
import org.aventyrs.core.campaign.SessionStatus;

public record SessionResponse(
        String id,
        int number,
        SessionStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant endedAt) {
}
