package org.aventyrs.api.campaign;

import java.util.List;
import org.aventyrs.core.campaign.SessionStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CampaignRepository extends MongoRepository<CampaignDocument, String> {

    /** Campanhas holding at least one Sessão in status. Matches on {@code sessions.status}. */
    List<CampaignDocument> findBySessionsStatus(SessionStatus status);
}
