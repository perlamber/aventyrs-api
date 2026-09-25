package org.aventyrs.api.campaign;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Persisted state backing an {@code org.aventyrs.core.campaign.Campaign}.
 *
 * <p><b>Participants are not stored here.</b> A sheet belongs to at most one Campanha, so the
 * membership record is {@code CharacterSheetDocument#campaignId}. {@link CampaignService} finds a
 * Campanha's participants by querying on that field.
 *
 * <p>{@code version} is the first {@code @Version} in this codebase. Each Sessão change is a
 * read-modify-write, and two GMs pressing "start" at once must not both succeed. The second save
 * fails with an optimistic-lock error, which {@code GlobalExceptionHandler} maps to {@code 409}.
 */
@Document(collection = "campaigns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CampaignDocument {

    @Id
    private String id;

    private String name;

    private List<SessionEntry> sessions;

    /**
     * The shared bag: loot the GM added or a Saquear took, waiting to be claimed by a participant.
     * {@code null} on a Campanha written before the bag existed. Every change re-reads and saves
     * under {@code version}, so two players cannot both claim the same entry.
     */
    private List<CampaignBagEntry> bag;

    private Instant createdAt;

    @Version
    private Long version;
}
