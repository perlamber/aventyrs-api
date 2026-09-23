package org.aventyrs.api.campaign;

import java.time.Instant;
import org.aventyrs.core.campaign.SessionStatus;

/**
 * Persisted mirror of a core {@code org.aventyrs.core.campaign.Session}. {@code startedAt}/{@code
 * endedAt} are this API's own bookkeeping, with no core counterpart, and are {@code null} until
 * that transition happens.
 */
public record SessionEntry(
        String id,
        int number,
        SessionStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant endedAt) {
}
