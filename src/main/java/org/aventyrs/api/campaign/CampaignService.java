package org.aventyrs.api.campaign;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import org.aventyrs.api.campaign.dto.CampaignCreateRequest;
import org.aventyrs.api.campaign.dto.CampaignResponse;
import org.aventyrs.api.campaign.dto.SessionResponse;
import org.aventyrs.api.common.NotFoundException;
import org.aventyrs.api.sheet.CharacterSheetDocument;
import org.aventyrs.api.sheet.CharacterSheetRepository;
import org.aventyrs.core.campaign.Campaign;
import org.aventyrs.core.campaign.Session;
import org.aventyrs.core.campaign.SessionStatus;
import org.springframework.stereotype.Service;

/**
 * Campanhas and their Sessões. Every Sessão change goes through core's {@link Campaign}. The
 * document is rebuilt into a {@code Campaign}, core applies the change (and throws {@code
 * IllegalOperationException}, a {@code 409}, when its rules refuse it), and the resulting Sessões
 * are written back. The document's {@code @Version} makes two concurrent changes fail rather than
 * both succeed.
 *
 * <p>This service stores the progression lock and reports it, but <b>does not enforce it</b>.
 * Enforcement is client-side by design (see {@code CharacterSheetResponse#progressionLocked}).
 */
@Service
public class CampaignService {

    private final CampaignRepository repository;
    private final CharacterSheetRepository characterSheetRepository;

    public CampaignService(CampaignRepository repository, CharacterSheetRepository characterSheetRepository) {
        this.repository = repository;
        this.characterSheetRepository = characterSheetRepository;
    }

    public CampaignResponse create(CampaignCreateRequest request) {
        Campaign campaign = Campaign.create(request.name());
        CampaignDocument document = new CampaignDocument(
                campaign.getId().toString(), campaign.getName(), List.of(), Instant.now(), null);
        return toResponse(repository.save(document));
    }

    public CampaignResponse get(String id) {
        return toResponse(findOrThrow(id));
    }

    public List<CampaignResponse> list() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    /** Adds the next Sessão in {@code CREATED}. A {@code 409} while another is not yet ENDED. */
    public CampaignResponse createSession(String id) {
        return changeSessions(id, campaign -> campaign.createSession().getNumber());
    }

    /** CREATED → ONGOING. A {@code 409} if another Sessão is ONGOING or this one isn't CREATED. */
    public CampaignResponse startSession(String id, int number) {
        return changeSessions(id, campaign -> campaign.startSession(number).getNumber());
    }

    /** ONGOING → ENDED. A {@code 409} if this Sessão isn't ONGOING. */
    public CampaignResponse endSession(String id, int number) {
        return changeSessions(id, campaign -> campaign.endSession(number).getNumber());
    }

    /**
     * Makes the sheet a participant of this Campanha. A sheet has at most one Campanha, so this
     * <em>moves</em> a sheet that was in another one. Idempotent. A {@code 404} if either id is unknown.
     */
    public CampaignResponse addParticipant(String id, String characterSheetId) {
        CampaignDocument campaign = findOrThrow(id);
        CharacterSheetDocument sheet = findSheetOrThrow(characterSheetId);
        sheet.setCampaignId(campaign.getId());
        characterSheetRepository.save(sheet);
        return toResponse(campaign);
    }

    /** A {@code 404} if the sheet is unknown or isn't a participant of this Campanha. */
    public CampaignResponse removeParticipant(String id, String characterSheetId) {
        CampaignDocument campaign = findOrThrow(id);
        CharacterSheetDocument sheet = findSheetOrThrow(characterSheetId);
        if (!campaign.getId().equals(sheet.getCampaignId())) {
            throw new NotFoundException("CharacterSheet " + characterSheetId + " is not in Campaign " + id);
        }
        sheet.setCampaignId(null);
        characterSheetRepository.save(sheet);
        return toResponse(campaign);
    }

    public void delete(String id) {
        findOrThrow(id);
        List<CharacterSheetDocument> participants = characterSheetRepository.findByCampaignId(id);
        participants.forEach(sheet -> sheet.setCampaignId(null));
        characterSheetRepository.saveAll(participants);
        repository.deleteById(id);
    }

    /** Whether the Campanha with this id has an ONGOING Sessão. {@code false} for {@code null} or an unknown id. */
    public boolean isProgressionLocked(String campaignId) {
        return campaignId != null && repository.findById(campaignId)
                .map(document -> toDomain(document).isProgressionLocked())
                .orElse(false);
    }

    /** Ids of every Campanha with an ONGOING Sessão, for answering many sheets with one query. */
    public Set<String> progressionLockedCampaignIds() {
        return repository.findBySessionsStatus(SessionStatus.ONGOING).stream()
                .map(CampaignDocument::getId)
                .collect(Collectors.toSet());
    }

    /**
     * Applies one Sessão change through core and writes the Sessões back. change returns the
     * number of the Sessão it touched, which is the one whose timestamp gets stamped.
     */
    private CampaignResponse changeSessions(String id, ToIntFunction<Campaign> change) {
        CampaignDocument document = findOrThrow(id);
        Campaign campaign = toDomain(document);

        int touched = change.applyAsInt(campaign);

        Map<String, SessionEntry> stored = currentSessions(document).stream()
                .collect(Collectors.toMap(SessionEntry::id, Function.identity()));
        Instant now = Instant.now();
        List<SessionEntry> updated = new ArrayList<>();
        for (Session session : campaign.getSessions()) {
            SessionEntry previous = stored.get(session.getId().toString());
            updated.add(session.getNumber() == touched ? stamp(previous, session, now) : previous);
        }
        document.setSessions(updated);
        return toResponse(repository.save(document));
    }

    private static SessionEntry stamp(SessionEntry previous, Session session, Instant now) {
        if (previous == null) {
            return new SessionEntry(session.getId().toString(), session.getNumber(), session.getStatus(), now, null, null);
        }
        return new SessionEntry(
                previous.id(),
                previous.number(),
                session.getStatus(),
                previous.createdAt(),
                session.getStatus() == SessionStatus.ONGOING ? now : previous.startedAt(),
                session.getStatus() == SessionStatus.ENDED ? now : previous.endedAt());
    }

    /**
     * Rebuilds the core Campanha. Participants are left out on purpose: no Sessão rule reads them,
     * and sheet ids are not guaranteed to be UUIDs.
     */
    private static Campaign toDomain(CampaignDocument document) {
        List<Session> sessions = currentSessions(document).stream()
                .map(entry -> Session.of(UUID.fromString(entry.id()), entry.number(), entry.status()))
                .toList();
        return Campaign.of(UUID.fromString(document.getId()), document.getName(), sessions, List.of());
    }

    /** {@code sessions} is never null once written by this service; normalised anyway, same as {@code SceneService}. */
    private static List<SessionEntry> currentSessions(CampaignDocument document) {
        return document.getSessions() == null ? List.of() : document.getSessions();
    }

    private CampaignDocument findOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Campaign not found: " + id));
    }

    private CharacterSheetDocument findSheetOrThrow(String id) {
        return characterSheetRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("CharacterSheet not found: " + id));
    }

    private CampaignResponse toResponse(CampaignDocument document) {
        List<SessionResponse> sessions = currentSessions(document).stream()
                .sorted(Comparator.comparingInt(SessionEntry::number))
                .map(entry -> new SessionResponse(entry.id(), entry.number(), entry.status(),
                        entry.createdAt(), entry.startedAt(), entry.endedAt()))
                .toList();
        Integer ongoing = sessions.stream()
                .filter(session -> session.status() == SessionStatus.ONGOING)
                .map(SessionResponse::number)
                .findFirst()
                .orElse(null);
        List<String> participants = characterSheetRepository.findByCampaignId(document.getId()).stream()
                .map(CharacterSheetDocument::getId)
                .filter(Objects::nonNull)
                .toList();
        return new CampaignResponse(document.getId(), document.getName(), sessions, participants,
                ongoing != null, ongoing, document.getCreatedAt());
    }
}
