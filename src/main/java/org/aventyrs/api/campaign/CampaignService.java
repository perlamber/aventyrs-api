package org.aventyrs.api.campaign;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import org.aventyrs.api.campaign.dto.BagAddRequest;
import org.aventyrs.api.campaign.dto.BagClaimRequest;
import org.aventyrs.api.campaign.dto.BagDepositRequest;
import org.aventyrs.api.campaign.dto.BagLootRequest;
import org.aventyrs.api.campaign.dto.CampaignBagItemResponse;
import org.aventyrs.api.campaign.dto.CampaignCreateRequest;
import org.aventyrs.api.campaign.dto.CampaignResponse;
import org.aventyrs.api.campaign.dto.SessionResponse;
import org.aventyrs.api.common.NotFoundException;
import org.aventyrs.api.item.InventoryItemEntry;
import org.aventyrs.api.item.InventoryItemMapper;
import org.aventyrs.api.monster.MonsterSheetDocument;
import org.aventyrs.api.monster.MonsterSheetRepository;
import org.aventyrs.api.sheet.CharacterSheetDocument;
import org.aventyrs.api.sheet.CharacterSheetRepository;
import org.aventyrs.core.campaign.Campaign;
import org.aventyrs.core.campaign.Session;
import org.aventyrs.core.campaign.SessionStatus;
import org.aventyrs.core.sheet.IllegalOperationException;
import org.aventyrs.core.util.TranslatableMessages;
import org.springframework.dao.OptimisticLockingFailureException;
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

    /** A bag entry named by a claim or a discard is gone, most likely claimed by someone else first. */
    public static final String BAG_ITEM_NOT_FOUND = "BAG_ITEM_NOT_FOUND";
    /** A claim or deposit named a sheet that isn't a participant of this Campanha. */
    public static final String NOT_A_CAMPAIGN_PARTICIPANT = "NOT_A_CAMPAIGN_PARTICIPANT";
    /** A deposit's index and item name no longer match the sheet's inventory. */
    public static final String INVENTORY_ITEM_NOT_FOUND = "INVENTORY_ITEM_NOT_FOUND";
    /** A Saquear found the foe carrying nothing. */
    public static final String NOTHING_TO_LOOT = "NOTHING_TO_LOOT";

    /**
     * How many times a bag change re-reads and retries after losing a version race. Additions and
     * removals of different entries don't conflict, so a lost race is almost always fine to redo.
     */
    private static final int BAG_WRITE_ATTEMPTS = 5;

    private final CampaignRepository repository;
    private final CharacterSheetRepository characterSheetRepository;
    private final MonsterSheetRepository monsterSheetRepository;

    public CampaignService(CampaignRepository repository, CharacterSheetRepository characterSheetRepository,
            MonsterSheetRepository monsterSheetRepository) {
        this.repository = repository;
        this.characterSheetRepository = characterSheetRepository;
        this.monsterSheetRepository = monsterSheetRepository;
    }

    public CampaignResponse create(CampaignCreateRequest request) {
        Campaign campaign = Campaign.create(request.name());
        CampaignDocument document = new CampaignDocument(
                campaign.getId().toString(), campaign.getName(), List.of(), List.of(), Instant.now(), null);
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

    // --- The bag ------------------------------------------------------------------------------
    //
    // The bag's rules (who may be looted, what a Saquear costs) are core's LootService and are
    // checked client-side, the same way the progression lock is. This service only makes each move
    // happen once: an item leaves one place and arrives in the other, and two players can't both
    // claim it. Moves between the Campanha and a sheet are two saves, not a transaction. The
    // Campanha side is always the versioned one, and if the second save fails the first is undone.

    /** The GM puts an item into the bag. */
    public CampaignResponse addToBag(String id, BagAddRequest request) {
        CampaignBagEntry entry = newEntry(InventoryItemMapper.toEntry(request.item()), request.sourceName());
        return toResponse(changeBag(id, bag -> bag.add(entry)));
    }

    /** The GM throws a bag item away. A {@code 409 BAG_ITEM_NOT_FOUND} if it is already gone. */
    public CampaignResponse discardFromBag(String id, String entryId) {
        return toResponse(changeBag(id, bag -> removeEntry(bag, entryId)));
    }

    /**
     * Moves a bag item into a participant's inventory. A {@code 409 NOT_A_CAMPAIGN_PARTICIPANT} if
     * the sheet isn't in this Campanha, or {@code BAG_ITEM_NOT_FOUND} if someone claimed it first.
     */
    public CampaignResponse claimFromBag(String id, String entryId, BagClaimRequest request) {
        CharacterSheetDocument sheet = findParticipantOrThrow(id, request.characterSheetId());
        CampaignBagEntry[] claimed = new CampaignBagEntry[1];
        CampaignDocument saved = changeBag(id, bag -> claimed[0] = removeEntry(bag, entryId));
        try {
            sheet = findSheetOrThrow(sheet.getId());
            sheet.setInventory(append(sheet.getInventory(), List.of(claimed[0].item())));
            characterSheetRepository.save(sheet);
        } catch (RuntimeException ex) {
            changeBag(id, bag -> bag.add(claimed[0]));
            throw ex;
        }
        return toResponse(saved);
    }

    /**
     * Moves a participant's carried item into the bag. Only carried items: what is worn has to be
     * taken off first. A {@code 409 INVENTORY_ITEM_NOT_FOUND} if the index and name no longer
     * match, or {@code NOT_A_CAMPAIGN_PARTICIPANT}.
     */
    public CampaignResponse depositToBag(String id, BagDepositRequest request) {
        CharacterSheetDocument sheet = findParticipantOrThrow(id, request.characterSheetId());
        List<InventoryItemEntry> inventory = new ArrayList<>(nullToEmpty(sheet.getInventory()));
        int index = request.inventoryIndex();
        if (index >= inventory.size() || !Objects.equals(inventory.get(index).name(), request.itemName())) {
            throw new IllegalOperationException(INVENTORY_ITEM_NOT_FOUND);
        }
        InventoryItemEntry item = inventory.remove(index);
        List<InventoryItemEntry> before = sheet.getInventory();
        sheet.setInventory(inventory);
        characterSheetRepository.save(sheet);

        CampaignBagEntry entry = newEntry(item, null);
        try {
            return toResponse(changeBag(id, bag -> bag.add(entry)));
        } catch (RuntimeException ex) {
            sheet.setInventory(before);
            characterSheetRepository.save(sheet);
            throw ex;
        }
    }

    /**
     * A Saquear, or the end-of-combat sweep: everything the foe carries moves into the bag, and
     * what it wears stays on it. sourceSheetId resolves against the monster sheets first, then the
     * character sheets (a GM's NPC), the same as a Cena participant id. A {@code 409
     * LOOT_TARGET_NOT_AN_ENEMY} for one of this Campanha's own participants, {@code NOTHING_TO_LOOT}
     * for an empty inventory, and a {@code 404} for an unknown id.
     */
    public CampaignResponse lootIntoBag(String id, BagLootRequest request) {
        findOrThrow(id);
        String sourceId = request.sourceSheetId();
        MonsterSheetDocument monster = monsterSheetRepository.findById(sourceId).orElse(null);
        CharacterSheetDocument npc = monster != null ? null : findSheetOrThrow(sourceId);
        if (npc != null && id.equals(npc.getCampaignId())) {
            throw new IllegalOperationException(TranslatableMessages.LOOT_TARGET_NOT_AN_ENEMY);
        }

        List<InventoryItemEntry> carried = nullToEmpty(monster != null ? monster.getInventory() : npc.getInventory());
        if (carried.isEmpty()) {
            throw new IllegalOperationException(NOTHING_TO_LOOT);
        }
        String sourceName = monster != null ? monster.getCharacter().name() : npc.getCharacter().name();
        setCarried(monster, npc, List.of());

        List<CampaignBagEntry> entries = carried.stream().map(item -> newEntry(item, sourceName)).toList();
        try {
            return toResponse(changeBag(id, bag -> bag.addAll(entries)));
        } catch (RuntimeException ex) {
            setCarried(monster, npc, carried);
            throw ex;
        }
    }

    private void setCarried(MonsterSheetDocument monster, CharacterSheetDocument npc, List<InventoryItemEntry> items) {
        if (monster != null) {
            monster.setInventory(items);
            monsterSheetRepository.save(monster);
        } else {
            npc.setInventory(items);
            characterSheetRepository.save(npc);
        }
    }

    /**
     * Re-reads the Campanha, applies change to a copy of its bag and saves under the document's
     * version, retrying a lost race. change may throw {@code IllegalOperationException} to refuse.
     */
    private CampaignDocument changeBag(String id, Consumer<List<CampaignBagEntry>> change) {
        for (int attempt = 1; ; attempt++) {
            CampaignDocument document = findOrThrow(id);
            List<CampaignBagEntry> bag = new ArrayList<>(nullToEmpty(document.getBag()));
            change.accept(bag);
            document.setBag(bag);
            try {
                return repository.save(document);
            } catch (OptimisticLockingFailureException ex) {
                if (attempt >= BAG_WRITE_ATTEMPTS) {
                    throw ex;
                }
            }
        }
    }

    private static CampaignBagEntry removeEntry(List<CampaignBagEntry> bag, String entryId) {
        CampaignBagEntry entry = bag.stream().filter(candidate -> candidate.id().equals(entryId)).findFirst()
                .orElseThrow(() -> new IllegalOperationException(BAG_ITEM_NOT_FOUND));
        bag.remove(entry);
        return entry;
    }

    private static CampaignBagEntry newEntry(InventoryItemEntry item, String sourceName) {
        return new CampaignBagEntry(UUID.randomUUID().toString(), item, sourceName, Instant.now());
    }

    private static <T> List<T> append(List<T> list, List<T> more) {
        List<T> result = new ArrayList<>(nullToEmpty(list));
        result.addAll(more);
        return result;
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private CharacterSheetDocument findParticipantOrThrow(String campaignId, String characterSheetId) {
        findOrThrow(campaignId);
        CharacterSheetDocument sheet = findSheetOrThrow(characterSheetId);
        if (!campaignId.equals(sheet.getCampaignId())) {
            throw new IllegalOperationException(NOT_A_CAMPAIGN_PARTICIPANT);
        }
        return sheet;
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
        List<CampaignBagItemResponse> bag = nullToEmpty(document.getBag()).stream()
                .map(entry -> new CampaignBagItemResponse(entry.id(), InventoryItemMapper.toDto(entry.item()),
                        entry.sourceName(), entry.addedAt()))
                .toList();
        return new CampaignResponse(document.getId(), document.getName(), sessions, participants,
                ongoing != null, ongoing, document.getCreatedAt(), bag);
    }
}
