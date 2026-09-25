package org.aventyrs.api.campaign.dto;

import java.time.Instant;
import java.util.List;

/**
 * A Campanha, its Sessões in number order, and the ids of its participant sheets.
 * {@code progressionLocked} is true while a Sessão is ONGOING, and {@code ongoingSessionNumber}
 * is that Sessão's number, or {@code null} when there is none.
 *
 * <p>Also the payload broadcast on {@code /topic/campaigns/{id}/sessions} after every Sessão
 * change and every participant change, so an open client can lock or unlock its progression UI
 * right away. {@code bag} is the shared loot, oldest first. The same response is broadcast on
 * {@code /topic/campaigns/{id}/bag} after every bag change.
 */
public record CampaignResponse(
        String id,
        String name,
        List<SessionResponse> sessions,
        List<String> participantSheetIds,
        boolean progressionLocked,
        Integer ongoingSessionNumber,
        Instant createdAt,
        List<CampaignBagItemResponse> bag) {
}
