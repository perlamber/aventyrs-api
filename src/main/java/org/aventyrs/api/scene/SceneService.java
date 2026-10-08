package org.aventyrs.api.scene;

import org.aventyrs.core.rest.RestType;
import org.aventyrs.api.scene.dto.SceneTimeMessage;
import org.aventyrs.api.scene.dto.SceneTimeEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.aventyrs.api.common.NotFoundException;
import org.aventyrs.api.scene.dto.AbilityActivatedEvent;
import org.aventyrs.api.scene.dto.AbilityActivationMessage;
import org.aventyrs.api.scene.dto.AddParticipantRequest;
import org.aventyrs.api.scene.dto.ConcealmentDto;
import org.aventyrs.api.scene.dto.GridPositionDto;
import org.aventyrs.api.scene.dto.GridResizedEvent;
import org.aventyrs.api.scene.dto.RecordActionMessage;
import org.aventyrs.api.scene.dto.RollRequestMessage;
import org.aventyrs.api.scene.dto.RollRequestedEvent;
import org.aventyrs.api.scene.dto.RollResponseMessage;
import org.aventyrs.api.scene.dto.RollRespondedEvent;
import org.aventyrs.api.scene.dto.SceneActionEvent;
import org.aventyrs.api.scene.dto.SceneConnectionResponse;
import org.aventyrs.api.scene.dto.SceneCreateRequest;
import org.aventyrs.api.scene.dto.SceneGroupResponse;
import org.aventyrs.api.scene.dto.SceneParticipantRequest;
import org.aventyrs.api.scene.dto.SceneParticipantResponse;
import org.aventyrs.api.scene.dto.SceneResponse;
import org.aventyrs.api.scene.dto.SceneUpdateRequest;
import org.aventyrs.api.scene.dto.TerrainPaintedEvent;
import org.aventyrs.api.scene.dto.TurnAdvancedEvent;
import org.aventyrs.api.monster.MonsterSheetRepository;
import org.aventyrs.api.sheet.CharacterSheetRepository;
import org.aventyrs.core.item.ItemRarity;
import org.aventyrs.core.item.ItemStore;
import org.aventyrs.core.scene.Direction;
import org.aventyrs.core.scene.TerrainType;
import org.aventyrs.core.scene.grid.GridPosition;
import org.aventyrs.core.sheet.ActionCost;
import org.aventyrs.core.sheet.ActionOutcome;
import org.aventyrs.core.sheet.IllegalOperationException;
import org.aventyrs.core.util.TranslatableMessages;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * CRUD plus the bits of action-time behavior this API does arbitrate: {@link #advanceTurn} and
 * {@link #startCombat}.
 *
 * <p>{@code SceneDocument#getParticipants()} is kept in a shape that mirrors core's {@code Scene}
 * exactly — its {@code activeEntries} and {@code pendingEntries} concatenated:
 *
 * <pre>[ rotation members, initiativeValue descending, stable ] ++ [ waiting joiners ]</pre>
 *
 * <p>Membership of the prefix is derived, never stored separately: an entry is in the rotation
 * exactly when {@code joinedAtRound <= currentRound} (see {@link SceneParticipantEntry}). Because
 * the rotation is the prefix, {@code currentIndex} indexes the participant list directly, the same
 * relationship core's own {@code currentIndex} has to its {@code activeEntries} — which is what
 * lets a client rebuild a real {@code Scene} around this cursor without a second ordering rule
 * living on the client side.
 */
@Service
public class SceneService {

    private final SceneRepository repository;
    private final CharacterSheetRepository characterSheetRepository;
    private final MonsterSheetRepository monsterSheetRepository;
    /** Used <b>only</b> by the two roll-request appends — see {@link #respondToRoll} for why those
     * cannot go through {@code repository.save} like every other mutation here. */
    private final MongoTemplate mongoTemplate;
    /** Stores and deletes an invoked creature's sheet (client 0.0.95). */
    private final org.aventyrs.api.monster.MonsterSheetService monsterSheetService;

    public SceneService(SceneRepository repository, CharacterSheetRepository characterSheetRepository,
            MonsterSheetRepository monsterSheetRepository, MongoTemplate mongoTemplate,
            org.aventyrs.api.monster.MonsterSheetService monsterSheetService) {
        this.monsterSheetService = monsterSheetService;
        this.monsterSheetRepository = monsterSheetRepository;
        this.repository = repository;
        this.characterSheetRepository = characterSheetRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public SceneResponse create(SceneCreateRequest request) {
        SceneDocument document = new SceneDocument(
                UUID.randomUUID().toString(), request.name(), TerrainType.valueOf(request.terrain()), List.of(), 0, -1,
                false, false, Map.of(), null, null, request.width(), request.height(), Instant.now(), List.of(),
                // abilityHistory, between actionHistory and rollRequests — @AllArgsConstructor
                // follows field declaration order.
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        return toResponse(repository.save(document));
    }

    public SceneResponse get(String id) {
        return toResponse(findOrThrow(id));
    }

    /**
     * The scene a client should drop into when it isn't told which. The active scene if there is
     * exactly one (see {@link SceneActivationService}); otherwise — none active, or more than one
     * left by a race between two activations — the most recently created, the same tiebreak this
     * endpoint used before {@code active} existed.
     */
    public SceneResponse getAvailable() {
        List<SceneDocument> active = repository.findByActiveTrue();
        SceneDocument chosen = active.size() == 1
                ? active.get(0)
                : repository.findTopByOrderByCreatedAtDesc()
                        .orElseThrow(() -> new NotFoundException("No scenes available"));
        return toResponse(chosen);
    }

    public List<SceneResponse> list() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    public List<SceneGroupResponse> listGroups(String id) {
        SceneDocument document = findOrThrow(id);
        Map<UUID, List<SceneParticipantResponse>> participantsByGroup = document.getParticipants().stream()
                .collect(Collectors.groupingBy(
                        SceneParticipantEntry::group,
                        LinkedHashMap::new,
                        Collectors.mapping(this::toParticipantResponse, Collectors.toList())));
        return participantsByGroup.entrySet().stream()
                .map(entry -> new SceneGroupResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    public SceneResponse update(String id, SceneUpdateRequest request) {
        SceneDocument document = findOrThrow(id);

        // The bulk PUT carries no concealment (a GM re-saving the Scene says nothing about who is
        // hiding), so whoever is Escondido stays so rather than being revealed by an edit.
        Map<String, SceneConcealmentEntry> concealments = new HashMap<>();
        document.getParticipants().stream()
                .filter(entry -> entry.concealment() != null)
                .forEach(entry -> concealments.put(entry.characterSheetId(), entry.concealment()));
        List<SceneParticipantEntry> participants = request.participants().stream()
                .map(this::toEntry)
                .map(entry -> entry.withConcealment(concealments.get(entry.characterSheetId())))
                .toList();
        requireParticipantsExist(participants);
        requireDistinctPositions(participants, sharedPositions(document.getParticipants()));
        requireValidTurnCursor(request.currentIndex(), rotationSize(participants, request.currentRound()));
        requireRoundGatedOnCombat(request.currentRound(), request.combatScene());

        document.setName(request.name());
        document.setParticipants(participants);
        document.setCurrentRound(request.currentRound());
        document.setCurrentIndex(request.currentIndex());
        document.setCombatScene(request.combatScene());
        document.setImageUrl(request.imageUrl());
        document.setItemStoreMaxRarity(toItemStoreMaxRarity(request.itemStoreMaxRarity()));

        return toResponse(repository.save(document));
    }

    /**
     * {@code null} passes through untouched — nowhere to shop, same as before the update. A
     * non-null rarity is round-tripped through a real {@link ItemStore} purely so its own
     * constructor enforces "must be a purchasable tier," the same way {@link #recordAction}
     * leans on {@code ActionCost}'s constructor rather than re-checking its invariant here.
     */
    private static ItemRarity toItemStoreMaxRarity(ItemRarity requested) {
        return requested == null ? null : new ItemStore(requested).getMaxRarity();
    }

    public SceneParticipantResponse addParticipant(String id, AddParticipantRequest request) {
        SceneDocument document = findOrThrow(id);
        requireCombatantExists(request.characterSheetId());

        List<SceneParticipantEntry> participants = new ArrayList<>(document.getParticipants());
        // Before anyone has acted the newcomer joins the rotation immediately, at its sorted spot;
        // once the cursor is moving it waits for the next Round instead, so it never shifts the
        // order out from under a Round already in progress. Same rule as Scene#addParticipant.
        boolean joinsNow = document.getCurrentIndex() == -1;
        SceneParticipantEntry entry = new SceneParticipantEntry(
                request.characterSheetId(),
                request.initiativeValue(),
                request.group(),
                firstFreePosition(participants),
                joinsNow ? document.getCurrentRound() : document.getCurrentRound() + 1,
                toConcealmentEntry(request.concealment()));
        if (joinsNow) {
            participants.add(rotationInsertionIndex(participants, document.getCurrentRound(), entry), entry);
        } else {
            participants.add(entry);
        }

        document.setParticipants(participants);
        repository.save(document);

        return toParticipantResponse(entry);
    }

    public void removeParticipant(String id, String characterSheetId) {
        SceneDocument document = findOrThrow(id);
        removeFromDocument(document, characterSheetId);
        repository.save(document);
    }

    /**
     * Takes characterSheetId out of document in memory: its summon record and sheet go with it if it is an
     * invocation, and a caster leaving takes its invocations and spawners along (client 0.0.95).
     */
    private void removeFromDocument(SceneDocument document, String characterSheetId) {
        List<SceneSummonEntry> summons = new ArrayList<>(summonsOf(document));
        boolean wasSummon = summons.removeIf(summon -> summon.summonId().equals(characterSheetId));
        List<String> ownSummons = summons.stream()
                .filter(summon -> summon.casterCharacterSheetId().equals(characterSheetId))
                .map(SceneSummonEntry::summonId)
                .toList();
        summons.removeIf(summon -> summon.casterCharacterSheetId().equals(characterSheetId));
        document.setSummons(summons);
        document.setSummonSpawners(spawnersOf(document).stream()
                .filter(spawner -> !spawner.casterCharacterSheetId().equals(characterSheetId))
                .toList());
        removeParticipantEntry(document, characterSheetId);
        if (wasSummon) {
            deleteSummonSheet(characterSheetId);
        }
        for (String summonId : ownSummons) {
            if (document.getParticipants().stream().anyMatch(entry -> entry.characterSheetId().equals(summonId))) {
                removeParticipantEntry(document, summonId);
            }
            deleteSummonSheet(summonId);
        }
    }

    private void deleteSummonSheet(String summonId) {
        if (monsterSheetRepository.existsById(summonId)) {
            monsterSheetService.delete(summonId);
        }
    }

    private void removeParticipantEntry(SceneDocument document, String characterSheetId) {
        List<SceneParticipantEntry> participants = new ArrayList<>(document.getParticipants());
        int removedIndex = indexOfParticipant(participants, characterSheetId);
        boolean wasInRotation = participants.get(removedIndex).joinedAtRound() <= document.getCurrentRound();
        participants.remove(removedIndex);

        // Keep the cursor on the same participant it was already on: removing someone at or before
        // it shifts everyone after them down one, so it has to move down too. Mirrors
        // Scene#removeParticipant.
        int currentIndex = document.getCurrentIndex();
        if (!hasRotationMember(participants, document.getCurrentRound())) {
            currentIndex = -1;
        } else if (wasInRotation && removedIndex <= currentIndex) {
            currentIndex--;
        }

        document.setParticipants(participants);
        document.setCurrentIndex(currentIndex);
    }

    // ---------- invocations (client 0.0.95) ----------

    /** The invocations standing in document — none on a document older than them. */
    private static List<SceneSummonEntry> summonsOf(SceneDocument document) {
        return document.getSummons() == null ? List.of() : document.getSummons();
    }

    private static List<SceneSummonSpawnerEntry> spawnersOf(SceneDocument document) {
        return document.getSummonSpawners() == null ? List.of() : document.getSummonSpawners();
    }

    /**
     * Puts an invoked creature into the Scene as its caster's own participant — core's {@code Scene#addSummons}: its
     * sheet stored for the caster's player, placed right after the caster (and their earlier summons) with the
     * caster's Iniciativa, group and rotation, and an earlier summon of its group marked to leave as the caster's Turn
     * ends.
     */
    public SceneParticipantResponse addSummon(String id, org.aventyrs.api.scene.dto.SummonCreateRequest request) {
        SceneDocument document = findOrThrow(id);
        SceneParticipantEntry entry = placeSummon(document, request.summonId(), request.casterCharacterSheetId(),
                new org.aventyrs.api.monster.SummonEntry(request.kind(), request.conjuradorManaGraduation(),
                        request.powers() == null ? List.of() : request.powers(), request.casterCharacterSheetId(),
                        request.enhancement(), request.familiar()),
                request.exclusivityGroup(), request.rounds(), request.concentration(), request.position());
        repository.save(document);
        return toParticipantResponse(entry);
    }

    private SceneParticipantEntry placeSummon(SceneDocument document, String summonId, String casterId,
            org.aventyrs.api.monster.SummonEntry summon, String exclusivityGroup, Integer rounds,
            boolean concentration, GridPosition requested) {
        List<SceneParticipantEntry> participants = new ArrayList<>(document.getParticipants());
        int casterIndex = indexOfParticipant(participants, casterId);
        SceneParticipantEntry caster = participants.get(casterIndex);
        List<SceneSummonEntry> summons = new ArrayList<>(summonsOf(document));
        if (exclusivityGroup != null) {
            summons.replaceAll(held -> held.casterCharacterSheetId().equals(casterId)
                    && exclusivityGroup.equals(held.exclusivityGroup()) ? held.markedReplaced() : held);
        }
        monsterSheetService.createSummon(summonId, summon, playerOf(casterId));

        GridPosition position = requested != null && isFree(participants, requested)
                ? requested : freePositionNear(participants, caster.position());
        SceneParticipantEntry entry = new SceneParticipantEntry(summonId, caster.effectiveInitiative(), caster.group(),
                position, caster.joinedAtRound(), null);
        // After the caster and the summons already behind it, in the order they were invoked.
        Set<String> casterSummons = summons.stream()
                .filter(held -> held.casterCharacterSheetId().equals(casterId))
                .map(SceneSummonEntry::summonId)
                .collect(Collectors.toSet());
        int at = casterIndex + 1;
        while (at < participants.size() && casterSummons.contains(participants.get(at).characterSheetId())) {
            at++;
        }
        participants.add(at, entry);
        boolean inRotation = caster.joinedAtRound() <= document.getCurrentRound();
        if (inRotation && document.getCurrentIndex() >= at) {
            document.setCurrentIndex(document.getCurrentIndex() + 1);
        }
        summons.add(new SceneSummonEntry(summonId, casterId, exclusivityGroup, concentration ? null : rounds,
                concentration ? (rounds == null ? 0 : rounds) : null, false));
        document.setParticipants(participants);
        document.setSummons(summons);
        return entry;
    }

    /** Sends an invocation away — it fell, or its caster dismissed it. */
    public void dismissSummon(String id, String summonId) {
        SceneDocument document = findOrThrow(id);
        removeFromDocument(document, summonId);
        repository.save(document);
    }

    /**
     * casterId's Concentração was lost (damage taken or an unpaid 1PA upkeep — core 0.1.1): every summon it held starts its trailing
     * Rodadas, and one with none leaves now. Returns whether anything changed.
     */
    public boolean releaseConcentration(String id, String casterId) {
        SceneDocument document = findOrThrow(id);
        List<SceneSummonEntry> summons = summonsOf(document);
        if (summons.stream().noneMatch(held -> held.casterCharacterSheetId().equals(casterId)
                && held.trailingRounds() != null)) {
            return false;
        }
        List<SceneSummonEntry> released = summons.stream()
                .map(held -> held.casterCharacterSheetId().equals(casterId) ? held.released() : held)
                .toList();
        document.setSummons(released);
        released.stream().filter(SceneSummonEntry::isSpent).map(SceneSummonEntry::summonId).toList()
                .forEach(summonId -> removeFromDocument(document, summonId));
        repository.save(document);
        return true;
    }

    /** Totem de Gaea: a creature for its caster now, and one at each Rodada boundary while it lasts. */
    public SceneParticipantResponse addSummonSpawner(String id,
            org.aventyrs.api.scene.dto.SummonSpawnerCreateRequest request) {
        SceneDocument document = findOrThrow(id);
        SceneSummonSpawnerEntry spawner = new SceneSummonSpawnerEntry(request.casterCharacterSheetId(),
                request.rounds(), request.kind(), request.conjuradorManaGraduation(),
                request.powers() == null ? List.of() : request.powers(), request.summonRounds(), request.enhancement());
        List<SceneSummonSpawnerEntry> spawners = new ArrayList<>(spawnersOf(document));
        spawners.add(spawner);
        document.setSummonSpawners(spawners);
        SceneParticipantEntry first = spawn(document, spawner);
        repository.save(document);
        return toParticipantResponse(first);
    }

    private SceneParticipantEntry spawn(SceneDocument document, SceneSummonSpawnerEntry spawner) {
        return placeSummon(document, UUID.randomUUID().toString(), spawner.casterCharacterSheetId(),
                new org.aventyrs.api.monster.SummonEntry(spawner.kind(), spawner.conjuradorManaGraduation(),
                        spawner.powers(), spawner.casterCharacterSheetId(), spawner.enhancement(), null),
                null, spawner.summonRounds(), false, null);
    }

    /** Whose player a combatant belongs to — the one who controls what they invoke. */
    private String playerOf(String combatantSheetId) {
        return characterSheetRepository.findById(combatantSheetId)
                .map(org.aventyrs.api.sheet.CharacterSheetDocument::getPlayerId)
                .or(() -> monsterSheetRepository.findById(combatantSheetId)
                        .map(org.aventyrs.api.monster.MonsterSheetDocument::getPlayerId))
                .orElseThrow(() -> new IllegalArgumentException("No CharacterSheet or MonsterSheet found: "
                        + combatantSheetId));
    }

    private static boolean isFree(List<SceneParticipantEntry> participants, GridPosition position) {
        return participants.stream().noneMatch(entry -> position.equals(entry.position()));
    }

    /** The free hex nearest centre, by ring — falling back to the first free one anywhere. */
    private GridPosition freePositionNear(List<SceneParticipantEntry> participants, GridPosition centre) {
        if (centre != null) {
            for (int radius = 1; radius < GridPosition.GRID_SIZE; radius++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        int x = centre.x() + dx;
                        int y = centre.y() + dy;
                        if (x < 0 || y < 0 || x >= GridPosition.GRID_SIZE || y >= GridPosition.GRID_SIZE) {
                            continue;
                        }
                        GridPosition candidate = new GridPosition(x, y);
                        if (isFree(participants, candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return firstFreePosition(participants);
    }

    public SceneParticipantEntry moveParticipant(String id, String characterSheetId, GridPosition newPosition) {
        return moveParticipant(id, characterSheetId, newPosition, false);
    }

    /**
     * Moves a participant, letting them end on an occupied hex when sharesSpace — Entre as Pernas'
     * "permanecer em um mesmo espaço ocupado por inimigo" (core {@code
     * MovementTerrainService#stepRules}). Whether the sharing is legal is judged by the moving
     * client, which holds both sheets; this API holds neither, so it trusts the flag — the same
     * client-side boundary every GM-only action here already has.
     */
    public SceneParticipantEntry moveParticipant(String id, String characterSheetId, GridPosition newPosition,
                                                 boolean sharesSpace) {
        SceneDocument document = findOrThrow(id);
        Set<GridPosition> permittedShared = new HashSet<>(sharedPositions(document.getParticipants()));
        if (sharesSpace) {
            permittedShared.add(newPosition);
        }

        List<SceneParticipantEntry> participants = new ArrayList<>(document.getParticipants());
        int index = indexOfParticipant(participants, characterSheetId);
        SceneParticipantEntry moved = participants.get(index).withPosition(newPosition);
        participants.set(index, moved);
        requireDistinctPositions(participants, permittedShared);

        document.setParticipants(participants);
        repository.save(document);

        return moved;
    }

    /**
     * Lines freshly-arrived travellers up against the edge they walked in through — the side of the
     * board facing {@code travelDirection.opposite()}, so a party heading {@code NORTH} lands along
     * the southern edge of the scene it enters. Each traveller keeps its place along that edge
     * relative to the others (their spread in the scene they left, re-centred on the middle of the
     * new edge); a cell that falls off the playable grid or is already taken is nudged to the
     * nearest free cell along the same edge, and failing that to any free cell.
     *
     * <p>{@code origins} maps a traveller's {@code characterSheetId} to where it stood in the origin
     * scene, and names only the participants that need placing — those just added to this scene.
     * Called from {@link SceneConnectionService#travel} inside its transaction, after the travellers
     * have been added at their provisional cells.
     */
    public void arrangeArrivals(String id, Map<String, GridPosition> origins, Direction travelDirection) {
        if (origins.isEmpty()) {
            return;
        }
        SceneDocument document = findOrThrow(id);
        List<SceneParticipantEntry> participants = new ArrayList<>(document.getParticipants());

        int width = document.getWidth() > 0 ? document.getWidth() : GridPosition.GRID_SIZE;
        int height = document.getHeight() > 0 ? document.getHeight() : GridPosition.GRID_SIZE;
        Direction edge = travelDirection.opposite();
        boolean alongX = edge == Direction.NORTH || edge == Direction.SOUTH;
        int span = alongX ? width : height;

        // Cells held by participants that already stood here and aren't being re-placed.
        Set<GridPosition> taken = new HashSet<>();
        for (SceneParticipantEntry participant : participants) {
            if (!origins.containsKey(participant.characterSheetId())) {
                taken.add(participant.position());
            }
        }

        // Walk the travellers in the order they stood along the edge axis, and re-centre that axis
        // on the middle of the new edge so the group keeps its shape and spacing.
        List<Map.Entry<String, GridPosition>> ordered = origins.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> edgeAxis(entry.getValue(), alongX)))
                .toList();
        double originCentre = ordered.stream()
                .mapToInt(entry -> edgeAxis(entry.getValue(), alongX)).average().orElse(0);
        double edgeCentre = (span - 1) / 2.0;

        Map<String, GridPosition> placed = new HashMap<>();
        for (Map.Entry<String, GridPosition> traveller : ordered) {
            int ideal = (int) Math.round(edgeCentre + (edgeAxis(traveller.getValue(), alongX) - originCentre));
            int slot = nearestFreeSlot(ideal, span, edge, width, height, taken);
            GridPosition position = slot >= 0
                    ? edgeCell(edge, slot, width, height)
                    : firstFreeCell(taken, width, height);
            taken.add(position);
            placed.put(traveller.getKey(), position);
        }

        for (int i = 0; i < participants.size(); i++) {
            SceneParticipantEntry entry = participants.get(i);
            GridPosition position = placed.get(entry.characterSheetId());
            if (position != null) {
                participants.set(i, entry.withPosition(position));
            }
        }
        requireDistinctPositions(participants, sharedPositions(document.getParticipants()));
        document.setParticipants(participants);
        repository.save(document);
    }

    private static int edgeAxis(GridPosition position, boolean alongX) {
        return alongX ? position.x() : position.y();
    }

    private static GridPosition edgeCell(Direction edge, int slot, int width, int height) {
        return switch (edge) {
            case NORTH -> new GridPosition(slot, 0);
            case SOUTH -> new GridPosition(slot, height - 1);
            case WEST -> new GridPosition(0, slot);
            case EAST -> new GridPosition(width - 1, slot);
        };
    }

    /** The slot nearest {@code ideal} along the arrival edge whose cell is on-grid and free,
     * searching outward in both directions, or {@code -1} if the whole edge is full. */
    private static int nearestFreeSlot(int ideal, int span, Direction edge, int width, int height,
            Set<GridPosition> taken) {
        for (int distance = 0; distance < span; distance++) {
            for (int slot : distance == 0 ? new int[] {ideal} : new int[] {ideal - distance, ideal + distance}) {
                if (slot >= 0 && slot < span && !taken.contains(edgeCell(edge, slot, width, height))) {
                    return slot;
                }
            }
        }
        return -1;
    }

    private static GridPosition firstFreeCell(Set<GridPosition> taken, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                GridPosition candidate = new GridPosition(x, y);
                if (!taken.contains(candidate)) {
                    return candidate;
                }
            }
        }
        throw new IllegalArgumentException("Scene grid is full");
    }

    /**
     * Redraws this scene's board at a new extent, in hex cells — the GM's grid-size control (see
     * {@link SceneRealtimeController#resizeGrid}). Unlike {@code terrain}, this is not fixed at
     * creation: the GM sizes the board to whatever the encounter needs, and every client draws the
     * extent stored here.
     *
     * <p>Shrinking is refused while a token would be left outside the new bounds rather than
     * silently dragging it inward. A participant's {@code position} is where the table agreed that
     * combatant is standing; a resize is a presentation decision, and letting one rewrite
     * positions would move tokens nobody moved. The GM shifts the strays in first, then resizes.
     *
     * @throws IllegalArgumentException if either dimension is outside {@code [1,
     *         GridPosition#GRID_SIZE]}, or a participant stands outside the requested extent
     */
    public GridResizedEvent resizeGrid(String id, int width, int height) {
        requireGridExtent(width, "width");
        requireGridExtent(height, "height");

        SceneDocument document = findOrThrow(id);
        requireParticipantsWithin(document.getParticipants(), width, height);

        document.setWidth(width);
        document.setHeight(height);
        // Terreno Difícil painted beyond the new edge has no board left to lie on.
        document.setDifficultTerrain(difficultTerrainOf(document).stream()
                .filter(cell -> cell.x() < width && cell.y() < height)
                .toList());
        repository.save(document);

        return new GridResizedEvent(width, height);
    }

    /**
     * The GM marks (difficult) or clears Terreno Difícil on cells, and gets back every difficult
     * hex the scene now has. Cells beyond the scene's extent are dropped rather than refused — a
     * drag that runs off the board still paints what it crossed. Only the GM's client offers it;
     * with no auth here that is a client-side restriction, as for {@link #resizeGrid}.
     */
    public TerrainPaintedEvent paintTerrain(String id, List<GridPosition> cells, boolean difficult) {
        SceneDocument document = findOrThrow(id);
        int width = document.getWidth() > 0 ? document.getWidth() : GridPosition.GRID_SIZE;
        int height = document.getHeight() > 0 ? document.getHeight() : GridPosition.GRID_SIZE;
        Set<GridPosition> painted = new LinkedHashSet<>(difficultTerrainOf(document));
        for (GridPosition cell : cells == null ? List.<GridPosition>of() : cells) {
            if (cell.x() >= width || cell.y() >= height) {
                continue;
            }
            if (difficult) {
                painted.add(cell);
            } else {
                painted.remove(cell);
            }
        }
        document.setDifficultTerrain(List.copyOf(painted));
        repository.save(document);
        return new TerrainPaintedEvent(painted.stream().map(cell -> new GridPositionDto(cell.x(), cell.y())).toList());
    }

    /** {@code difficultTerrain}, null-safe — absent on a document persisted before it existed. */
    private static List<GridPosition> difficultTerrainOf(SceneDocument document) {
        return document.getDifficultTerrain() == null ? List.of() : document.getDifficultTerrain();
    }

    private static void requireGridExtent(int dimension, String name) {
        if (dimension < 1 || dimension > GridPosition.GRID_SIZE) {
            throw new IllegalArgumentException(
                    "Grid " + name + " out of bounds [1," + GridPosition.GRID_SIZE + "]: " + dimension);
        }
    }

    private static void requireParticipantsWithin(List<SceneParticipantEntry> participants, int width, int height) {
        List<String> stranded = participants.stream()
                .filter(entry -> entry.position().x() >= width || entry.position().y() >= height)
                .map(SceneParticipantEntry::characterSheetId)
                .toList();
        if (!stranded.isEmpty()) {
            throw new IllegalArgumentException(
                    "Participants would fall outside a " + width + "x" + height + " grid: " + stranded);
        }
    }

    /**
     * Combat breaks out in this scene, mirroring {@code Scene#startCombat()} (core 0.0.32): flip
     * {@code combatScene} on so the Rodada counter and the Round-boundary bookkeeping in {@link
     * #advanceTurn} start running. Idempotent within a scene — a second call throws rather than
     * re-firing, the same guard core's {@code startCombat()} carries.
     *
     * <p>What core's {@code startCombat()} also does — running {@code CombatantSheet#startCombat()}
     * on every participant to apply start-of-combat Talento Blessings ({@code
     * AnaoFeat#VIGOR_DO_INVERNO}) — is left to the clients, the same split {@link #advanceTurn}
     * documents for {@code finishTurn()}/{@code startTurn(int)}: the live {@code CombatantSheet}s
     * those Blessings land on exist only there. Each client runs its own {@code Scene#startCombat()}
     * off the broadcast this produces.
     *
     * <p>A scene rebuilt from persistence already mid-combat keeps using {@link #update} (the full
     * PUT), which sets {@code combatScene} straight through without re-firing anything — the same
     * role core's {@code Scene#setCombatScene(boolean)} keeps alongside {@code startCombat()}.
     *
     * @throws IllegalOperationException ({@code SCENE_ALREADY_IN_COMBAT}) if this scene is already
     *         a combat scene
     */
    public SceneResponse startCombat(String id) {
        SceneDocument document = findOrThrow(id);
        if (document.isCombatScene()) {
            throw new IllegalOperationException(TranslatableMessages.SCENE_ALREADY_IN_COMBAT);
        }

        document.setCombatScene(true);
        return toResponse(repository.save(document));
    }

    /**
     * Combat ends in this scene — the GM's "encerrar combate", mirroring {@code Scene#endCombat()}
     * (core 0.0.48): flip {@code combatScene} off and put {@code currentRound} back to 0, so the
     * next combat counts its Rodadas afresh. The turn cursor is left where it is.
     *
     * <p>What core's {@code endCombat()} also does — dropping every participant's combat-scoped
     * grants ("até o final da Cena": Campeão da Taverna's stacked Defesas, Impacto Elemental's
     * budget) — is left to the clients, the same split {@link #startCombat} documents: those
     * grants live on the {@code CombatantSheet}s that exist only there. Each client runs its own
     * {@code Scene#endCombat()} off the broadcast this produces.
     *
     * @throws IllegalOperationException ({@code SCENE_NOT_IN_COMBAT}) if this scene is not a combat
     *         scene
     */
    public SceneResponse endCombat(String id) {
        SceneDocument document = findOrThrow(id);
        if (!document.isCombatScene()) {
            throw new IllegalOperationException(TranslatableMessages.SCENE_NOT_IN_COMBAT);
        }

        document.setCombatScene(false);
        document.setCurrentRound(0);
        return toResponse(repository.save(document));
    }

    /**
     * Moves this scene's turn cursor on by one, mirroring {@code Scene#next()}'s arithmetic: step
     * to the next participant in the rotation, and on wrapping back to the top — <b>only while
     * {@code combatScene} is true</b> — advance the Round, merge whoever was waiting, and re-derive
     * the order from everyone's {@code initiativeValue}. Before combat has started the wrap just
     * cycles the cursor: {@code currentRound} stays 0 and no Round boundary fires, the Rodada gate
     * core added in 0.0.32 ({@code Scene#next()} — "a Rodada is a combat unit").
     *
     * <p>Only the cursor moves here. {@code Scene#next()}'s other half — {@code finishTurn()} on
     * whoever's Turn just ended and {@code startTurn(int)} on whoever's beginning — deliberately
     * doesn't happen server-side and can't: participants are stored as CharacterSheet ids, and the
     * live {@code CombatantSheet}s those callbacks act on (with their in-flight temporary effects)
     * exist only on the clients. Each client runs its own {@code next()} off the broadcast this
     * produces, which is where that lifecycle actually fires. This method is what makes them all
     * agree on the cursor rather than each drifting on its own copy.
     * @return the cursor this scene is now on, and whose Turn it is
     * @throws IllegalArgumentException if no participant is in the rotation yet
     */
    public TurnAdvancedEvent advanceTurn(String id) {
        return advanceTurnReporting(id).event();
    }

    /** A turn advance, and whether invocations joined or left with it — the roster then needs a re-broadcast. */
    public record TurnAdvance(TurnAdvancedEvent event, boolean rosterChanged) {
    }

    /**
     * {@link #advanceTurn}, plus what core's {@code Scene#next()} does to invocations (client 0.0.95): a summon its
     * group replaced leaves as its caster's Turn ends; at the Rodada boundary each summon's Duração ticks and a spent
     * one leaves, and each spawner invokes its next creature.
     */
    public TurnAdvance advanceTurnReporting(String id) {
        SceneDocument document = findOrThrow(id);
        boolean rosterChanged = false;
        int round = document.getCurrentRound();

        int finishingIndex = document.getCurrentIndex();
        if (finishingIndex >= 0 && finishingIndex < rotationSize(document.getParticipants(), round)) {
            String finishing = document.getParticipants().get(finishingIndex).characterSheetId();
            List<String> replaced = summonsOf(document).stream()
                    .filter(held -> held.replaced() && held.casterCharacterSheetId().equals(finishing))
                    .map(SceneSummonEntry::summonId)
                    .toList();
            for (String summonId : replaced) {
                removeFromDocument(document, summonId);
                rosterChanged = true;
            }
        }

        List<SceneParticipantEntry> participants = new ArrayList<>(document.getParticipants());
        int rotationSize = rotationSize(participants, round);
        if (rotationSize == 0) {
            throw new IllegalArgumentException("No participants in scene: " + id);
        }

        int index = document.getCurrentIndex() + 1;
        boolean wrapped = false;
        if (index >= rotationSize) {
            index = 0;
            if (document.isCombatScene()) {
                round++;
                participants = mergeAndSortRotation(participants, round);
                wrapped = true;
            }
        }

        document.setParticipants(participants);
        document.setCurrentRound(round);
        document.setCurrentIndex(index);
        if (wrapped) {
            rosterChanged |= runRoundBoundaryForSummons(document);
            // A summon leaving at the boundary may have stood at the top of the order.
            document.setCurrentIndex(hasRotationMember(document.getParticipants(), round) ? 0 : -1);
        }
        repository.save(document);

        int current = Math.max(0, document.getCurrentIndex());
        return new TurnAdvance(new TurnAdvancedEvent(document.getParticipants().get(current).characterSheetId(),
                round, document.getCurrentIndex()), rosterChanged);
    }

    /** The Rodada boundary for invocations — spent Durações leave, spawners invoke. Whether the roster changed. */
    private boolean runRoundBoundaryForSummons(SceneDocument document) {
        boolean changed = false;
        List<SceneSummonEntry> ticked = summonsOf(document).stream().map(SceneSummonEntry::ticked).toList();
        document.setSummons(ticked);
        for (SceneSummonEntry spent : ticked.stream().filter(SceneSummonEntry::isSpent).toList()) {
            removeFromDocument(document, spent.summonId());
            changed = true;
        }
        List<SceneSummonSpawnerEntry> standing = new ArrayList<>();
        for (SceneSummonSpawnerEntry spawner : spawnersOf(document)) {
            SceneSummonSpawnerEntry next = spawner.ticked();
            if (next.remainingRounds() > 0) {
                standing.add(next);
            }
        }
        document.setSummonSpawners(standing);
        for (SceneSummonSpawnerEntry spawner : standing) {
            if (document.getParticipants().stream()
                    .anyMatch(entry -> entry.characterSheetId().equals(spawner.casterCharacterSheetId()))) {
                spawn(document, spawner);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * The Round-boundary bookkeeping {@link #advanceTurn} runs on every wrap, mirroring {@code
     * Scene#startNewRound()}: everyone whose {@code joinedAtRound} has now come round joins the
     * rotation prefix, which is then re-sorted by {@code initiativeValue} descending. The sort is
     * stable, so ties keep the order they already had — including a joiner tying with someone
     * already there, which is the same tie behavior {@link #rotationInsertionIndex} preserves.
     */
    private List<SceneParticipantEntry> mergeAndSortRotation(List<SceneParticipantEntry> participants, int round) {
        // Each Iniciativa override one boundary on before the sort (SceneInitiativeOverrideEntry), so it governs
        // exactly the Rodadas it was bought for.
        List<SceneParticipantEntry> rotation = new ArrayList<>(participants.stream()
                .filter(entry -> entry.joinedAtRound() <= round)
                .map(entry -> entry.initiativeOverride() == null ? entry
                        : entry.withInitiativeOverride(entry.initiativeOverride().advanced()))
                .toList());
        rotation.sort(Comparator.comparingInt(SceneParticipantEntry::effectiveInitiative).reversed());

        List<SceneParticipantEntry> merged = new ArrayList<>(rotation);
        participants.stream().filter(entry -> entry.joinedAtRound() > round).forEach(merged::add);
        return merged;
    }

    /**
     * Appends one resolved {@code CombatantAction} to this scene's permanent combat log, mirroring
     * core's {@code Scene#recordAction(CombatantSheet, CombatantAction)} — never cleared, unlike
     * the per-Rodada/per-Cena logs a live {@code CombatantSheet} keeps client-side. This server
     * never runs the rules engine itself (see this class's own javadoc), so message is taken as
     * whatever the sending client already resolved, the same trust {@link #moveParticipant} places
     * in a reported grid position.
     * @throws org.aventyrs.core.sheet.IllegalOperationException if the {@code ActionCost} message
     *         describes is internally inconsistent (e.g. {@code ACTION_POINTS} with 0 points)
     * @throws NotFoundException if characterSheetId is not a participant of this scene
     */
    public SceneActionEvent recordAction(String id, RecordActionMessage message) {
        SceneDocument document = findOrThrow(id);
        indexOfParticipant(document.getParticipants(), message.characterSheetId());

        SceneActionEntry entry = new SceneActionEntry(
                message.characterSheetId(),
                message.skill(),
                message.governingDomain(),
                message.attackSourceKind(),
                new ActionCost(message.costKind(), message.actionPoints()),
                message.turnNumber(),
                toOutcome(message),
                message.dice() == null ? null : List.copyOf(message.dice()),
                message.total(),
                message.targetCharacterSheetId(),
                message.activatedFeats() == null ? null : List.copyOf(message.activatedFeats()),
                message.attackDetails());

        List<SceneActionEntry> history = new ArrayList<>(actionHistoryOf(document));
        history.add(entry);
        document.setActionHistory(history);
        repository.save(document);

        return toActionEvent(entry);
    }

    /** {@code null} when none of the outcome fields were stated, same tri-state {@code
     * ActionOutcome} itself preserves for {@code succeeded}/{@code margin} alone. */
    private static ActionOutcome toOutcome(RecordActionMessage message) {
        if (message.succeeded() == null && message.margin() == null
                && message.criticalResult() == null && message.reachedDifficultyLevel() == null) {
            return null;
        }
        return new ActionOutcome(message.succeeded(), message.margin(),
                message.criticalResult(), message.reachedDifficultyLevel());
    }

    /**
     * Records a Habilidade de Título one client just activated, and hands back the event to
     * broadcast.
     *
     * <p>Persisted onto the Scene's own log beside {@code actionHistory}, and for the same reason:
     * a client joining late has no other way to learn that an Aura is standing or that an ally's
     * Defesas were raised three Rodadas ago. Taken at face value — this server runs no rules
     * engine, so what the activating client's core decided is what is stored.
     *
     * @throws NotFoundException if characterSheetId is not a participant of this scene
     */
    public AbilityActivatedEvent recordAbility(String id, AbilityActivationMessage message) {
        SceneDocument document = findOrThrow(id);
        indexOfParticipant(document.getParticipants(), message.characterSheetId());

        SceneAbilityEntry entry = new SceneAbilityEntry(
                message.characterSheetId(),
                message.titleType(),
                message.abilityId(),
                message.abilityName(),
                message.determinationPointsSpent(),
                message.hitPointsSpent(),
                message.turnNumber(),
                message.blessings() == null ? List.of() : List.copyOf(message.blessings()),
                message.enchanterCharacterSheetId(),
                message.boundCharacterSheetIds() == null
                        ? List.of() : List.copyOf(message.boundCharacterSheetIds()),
                message.enchantmentRounds(),
                message.effects(),
                message.skillType());

        List<SceneAbilityEntry> history = new ArrayList<>(abilityHistoryOf(document));
        history.add(entry);
        document.setAbilityHistory(history);
        repository.save(document);

        return toAbilityEvent(entry);
    }

    /** {@code null} on any document persisted before {@code abilityHistory} existed. */
    private static List<SceneAbilityEntry> abilityHistoryOf(SceneDocument document) {
        return document.getAbilityHistory() == null ? List.of() : document.getAbilityHistory();
    }

    private AbilityActivatedEvent toAbilityEvent(SceneAbilityEntry entry) {
        return new AbilityActivatedEvent(
                entry.characterSheetId(),
                entry.titleType(),
                entry.abilityId(),
                entry.abilityName(),
                SceneAbilityEntry.orZero(entry.determinationPointsSpent()),
                SceneAbilityEntry.orZero(entry.hitPointsSpent()),
                SceneAbilityEntry.orZero(entry.turnNumber()),
                entry.blessings() == null ? List.of() : List.copyOf(entry.blessings()),
                entry.enchanterCharacterSheetId(),
                entry.boundCharacterSheetIds() == null
                        ? List.of() : List.copyOf(entry.boundCharacterSheetIds()),
                SceneAbilityEntry.orZero(entry.enchantmentRounds()),
                entry.effects(),
                entry.skillType());
    }

    /** {@code null} on any document persisted before {@code actionHistory} existed. */
    private static List<SceneActionEntry> actionHistoryOf(SceneDocument document) {
        return document.getActionHistory() == null ? List.of() : document.getActionHistory();
    }

    /**
     * Records a roll the Narrador is asking the table for, and hands back the event to broadcast.
     *
     * <p>The {@code requestId} is stamped here rather than accepted from the client, so responses
     * have something stable to point at that two clients could not collide on — the same reason
     * {@code ping} stamps its own {@code Instant}.
     *
     * <p>Nothing validates that the sender is the Narrador, because nothing could: this API has no
     * authentication and {@code PlayerRole} is documented as not being an authorization boundary.
     * The named targets <em>are</em> checked for membership of this scene, which is the same guard
     * {@link #moveParticipant} applies — it asserts the id belongs here, never that the sender owns
     * it.
     *
     * @throws NotFoundException if a named target is not a participant of this scene
     */
    public RollRequestedEvent requestRoll(String id, RollRequestMessage message) {
        SceneDocument document = findOrThrow(id);
        List<String> targets = message.targetCharacterSheetIds() == null
                ? List.of() : List.copyOf(message.targetCharacterSheetIds());
        for (String target : targets) {
            indexOfParticipant(document.getParticipants(), target);
        }

        SceneRollRequestEntry entry = new SceneRollRequestEntry(
                UUID.randomUUID().toString(),
                message.kind(),
                message.skill(),
                message.difficultyLevel(),
                message.attackBonus(),
                message.attackerCharacterSheetId(),
                targets,
                message.prompt(),
                Instant.now(),
                message.specialization());

        appendTo(id, "rollRequests", entry);
        return toRollRequestedEvent(entry);
    }

    /**
     * Records one player's answer and hands back the event to broadcast.
     *
     * <p><b>Appended with an atomic {@code $push}, not a read-modify-write save.</b> Every other
     * mutation in this class loads the document, mutates it and calls {@code repository.save},
     * which replaces the whole thing — and no document in this codebase carries a {@code @Version},
     * so two concurrent saves silently lose one of the writes. Everywhere else that races rarely.
     * Here it is the <em>normal</em> case: the Narrador asks the whole table for an Atenção roll and
     * several players answer within the same second, on the broker's inbound thread pool. A
     * targeted {@code $push} lets the database serialise the appends instead.
     *
     * @throws NotFoundException if characterSheetId is not a participant of this scene
     */
    public RollRespondedEvent respondToRoll(String id, RollResponseMessage message) {
        SceneDocument document = findOrThrow(id);
        indexOfParticipant(document.getParticipants(), message.characterSheetId());

        SceneRollResponseEntry entry = new SceneRollResponseEntry(
                message.requestId(),
                message.characterSheetId(),
                message.kind(),
                message.dice() == null ? List.of() : List.copyOf(message.dice()),
                message.succeeded(),
                message.margin(),
                message.total(),
                message.requiredTotal(),
                message.note(),
                Instant.now(),
                message.interceptedForCharacterSheetId());

        appendTo(id, "rollResponses", entry);
        return toRollRespondedEvent(entry);
    }

    /** One atomic array append — see {@link #respondToRoll} for why this exists at all. */
    private void appendTo(String sceneId, String field, Object entry) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(sceneId)),
                new Update().push(field, entry),
                SceneDocument.class);
    }

    /** {@code null} on any document persisted before roll requests existed. */
    private static List<SceneRollRequestEntry> rollRequestsOf(SceneDocument document) {
        return document.getRollRequests() == null ? List.of() : document.getRollRequests();
    }

    private static List<SceneRollResponseEntry> rollResponsesOf(SceneDocument document) {
        return document.getRollResponses() == null ? List.of() : document.getRollResponses();
    }

    private RollRequestedEvent toRollRequestedEvent(SceneRollRequestEntry entry) {
        return new RollRequestedEvent(entry.requestId(), entry.kind(), entry.skill(),
                entry.difficultyLevel(), entry.attackBonus(), entry.attackerCharacterSheetId(),
                entry.targetCharacterSheetIds(), entry.prompt(), entry.requestedAt(), entry.specialization());
    }

    private RollRespondedEvent toRollRespondedEvent(SceneRollResponseEntry entry) {
        return new RollRespondedEvent(entry.requestId(), entry.characterSheetId(), entry.kind(),
                entry.dice(), entry.succeeded(), entry.margin(), entry.total(), entry.requiredTotal(),
                entry.note(), entry.respondedAt(), entry.interceptedForCharacterSheetId());
    }

    private SceneActionEvent toActionEvent(SceneActionEntry entry) {
        ActionOutcome outcome = entry.outcome();
        return new SceneActionEvent(
                entry.characterSheetId(),
                entry.skill(),
                entry.governingDomain(),
                entry.attackSourceKind(),
                entry.cost().kind(),
                entry.cost().actionPoints(),
                entry.turnNumber(),
                outcome == null ? null : outcome.succeeded(),
                outcome == null ? null : outcome.margin(),
                outcome == null ? null : outcome.criticalResult(),
                outcome == null ? null : outcome.reachedDifficultyLevel(),
                entry.dice(),
                entry.total(),
                entry.targetCharacterSheetId(),
                entry.activatedFeats(),
                entry.attackDetails());
    }

    /** How many of participants are in the turn rotation at round — the length of the list's prefix. */
    private int rotationSize(List<SceneParticipantEntry> participants, int round) {
        return (int) participants.stream().filter(entry -> entry.joinedAtRound() <= round).count();
    }

    private boolean hasRotationMember(List<SceneParticipantEntry> participants, int round) {
        return participants.stream().anyMatch(entry -> entry.joinedAtRound() <= round);
    }

    /** Where entry belongs in the rotation prefix: before the first member with a strictly lower
     * initiative value, keeping ties in insertion order. Mirrors {@code Scene#insertSorted}. */
    private int rotationInsertionIndex(List<SceneParticipantEntry> participants, int round, SceneParticipantEntry entry) {
        int rotationSize = rotationSize(participants, round);
        for (int i = 0; i < rotationSize; i++) {
            if (participants.get(i).effectiveInitiative() < entry.effectiveInitiative()) {
                return i;
            }
        }
        return rotationSize;
    }

    /**
     * Throws unless {@code characterSheetId} is actually in this scene. There's no auth on this
     * API yet, so it's the only thing standing between a scene's status topic and a client
     * broadcasting combat state for a sheet that has nothing to do with that scene — {@link
     * #moveParticipant} gets the same guarantee for free from {@link #indexOfParticipant}.
     */
    /**
     * Checks a GM's "Passar tempo"/"Descansar" against this scene — hours not negative, a known
     * {@code RestType}, every named sheet a participant — and returns what to broadcast. Nothing is
     * persisted here: each client applies the time to the core sheets it owns and reports the result
     * on {@code /status}, the same split every other rules effect keeps.
     *
     * @throws IllegalArgumentException for negative hours or an unknown rest type
     */
    public SceneTimeEvent passTime(String id, SceneTimeMessage message) {
        SceneDocument document = findOrThrow(id);
        if (message.hours() < 0) {
            throw new IllegalArgumentException("Hours cannot be negative: " + message.hours());
        }
        if (message.restType() != null) {
            RestType.valueOf(message.restType());
        }
        List<String> resting = message.characterSheetIds() == null ? List.of() : List.copyOf(message.characterSheetIds());
        resting.forEach(sheetId -> indexOfParticipant(document.getParticipants(), sheetId));
        return new SceneTimeEvent(message.hours(), message.restType(), resting);
    }

    /**
     * Puts an Iniciativa Ego point's value in place of a participant's rolled Iniciativa (core 0.0.79) —
     * {@code rodadas} Rodadas of the order ({@code null}: the rest of the Cena), taking hold at the next Rodada
     * boundary. Replaces an override already there. The client resolved and paid it through core; this persists
     * what it decided, as {@link #setConcealment} does.
     */
    public void setInitiativeOverride(String id, String characterSheetId, int value, Integer rodadas) {
        if (rodadas != null && rodadas < 1) {
            throw new IllegalArgumentException("An Iniciativa override governs at least one Rodada: " + rodadas);
        }
        SceneDocument document = findOrThrow(id);
        List<SceneParticipantEntry> participants = new ArrayList<>(document.getParticipants());
        int index = indexOfParticipant(participants, characterSheetId);
        participants.set(index, participants.get(index)
                .withInitiativeOverride(new SceneInitiativeOverrideEntry(value, rodadas, false)));
        document.setParticipants(participants);
        repository.save(document);
    }

    public void requireParticipant(String id, String characterSheetId) {
        indexOfParticipant(findOrThrow(id).getParticipants(), characterSheetId);
    }

    /**
     * Records a participant's concealment starting ({@code concealment} non-{@code null}) or ending
     * ({@code null}) — what the {@code /hidden} relay persists before broadcasting, so the Scene
     * payload a later joiner reads agrees with what the live boards were told.
     */
    public void setConcealment(String id, String characterSheetId, ConcealmentDto concealment) {
        SceneDocument document = findOrThrow(id);
        List<SceneParticipantEntry> participants = new ArrayList<>(document.getParticipants());
        int index = indexOfParticipant(participants, characterSheetId);
        participants.set(index, participants.get(index).withConcealment(toConcealmentEntry(concealment)));
        document.setParticipants(participants);
        repository.save(document);
    }

    private int indexOfParticipant(List<SceneParticipantEntry> participants, String characterSheetId) {
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).characterSheetId().equals(characterSheetId)) {
                return i;
            }
        }
        throw new NotFoundException("Participant not found in scene: " + characterSheetId);
    }

    private GridPosition firstFreePosition(List<SceneParticipantEntry> participants) {
        Set<GridPosition> occupied = participants.stream()
                .map(SceneParticipantEntry::position)
                .collect(Collectors.toSet());
        for (int y = 0; y < GridPosition.GRID_SIZE; y++) {
            for (int x = 0; x < GridPosition.GRID_SIZE; x++) {
                GridPosition candidate = new GridPosition(x, y);
                if (!occupied.contains(candidate)) {
                    return candidate;
                }
            }
        }
        throw new IllegalArgumentException("Scene grid is full");
    }

    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("Scene not found: " + id);
        }
        repository.deleteById(id);
    }

    private void requireParticipantsExist(List<SceneParticipantEntry> participants) {
        for (SceneParticipantEntry participant : participants) {
            requireCombatantExists(participant.characterSheetId());
        }
    }

    /**
     * A participant is whatever the id resolves to — a CharacterSheet or a MonsterSheet.
     *
     * <p>{@code SceneParticipantEntry} carries no discriminator, and deliberately so: core's own
     * {@code Scene#addParticipant} is typed {@code CombatantSheet} and has no monster-specific
     * entry point either, so a Scene has never needed to know which kind of combatant is standing
     * in it. Ids are random UUIDs across both collections, so "exists in either" is unambiguous.
     *
     * <p>The cost is one extra existence check per participant on the miss path, and a client
     * resolving a participant for display has to ask both endpoints. That is a smaller price than
     * a stored kind — which would need a discriminator on the entry, a backfill for every Scene
     * already persisted, and a decision at every call site that currently just holds an id.
     */
    private void requireCombatantExists(String combatantSheetId) {
        if (characterSheetRepository.existsById(combatantSheetId)
                || monsterSheetRepository.existsById(combatantSheetId)) {
            return;
        }
        throw new IllegalArgumentException("No CharacterSheet or MonsterSheet found: " + combatantSheetId);
    }

    /**
     * Two participants may not stand on one hex — except on a hex in permittedShared: one a move
     * was explicitly allowed to share ({@link #moveParticipant(String, String, GridPosition, boolean)}),
     * or one already shared in the persisted scene, so a later full {@code PUT} carrying the same
     * positions back is not refused for a sharing this API already accepted.
     */
    private void requireDistinctPositions(List<SceneParticipantEntry> participants,
                                          Set<GridPosition> permittedShared) {
        Map<GridPosition, Long> counts = participants.stream()
                .collect(Collectors.groupingBy(SceneParticipantEntry::position, Collectors.counting()));
        boolean clash = counts.entrySet().stream()
                .anyMatch(entry -> entry.getValue() > 1 && !permittedShared.contains(entry.getKey()));
        if (clash) {
            throw new IllegalArgumentException("Two participants cannot occupy the same grid position");
        }
    }

    /** The hexes more than one of participants already stands on. */
    private static Set<GridPosition> sharedPositions(List<SceneParticipantEntry> participants) {
        if (participants == null) {
            return Set.of();
        }
        return participants.stream()
                .collect(Collectors.groupingBy(SceneParticipantEntry::position, Collectors.counting()))
                .entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /** A Rodada is a combat unit (core 0.0.32): a scene that isn't a combat scene is on Round 0 by
     * definition, so a full replace can't restore one to a later Round without also marking it in
     * combat. A mid-combat scene rebuilt from persistence sends {@code combatScene: true} alongside
     * its round, the same pairing core's {@code setCombatScene} + {@code restoreTurnCursor} expect. */
    private static void requireRoundGatedOnCombat(int currentRound, boolean combatScene) {
        if (currentRound != 0 && !combatScene) {
            throw new IllegalArgumentException(
                    "currentRound must be 0 unless combatScene is true (a Rodada only elapses in combat)");
        }
    }

    /** The cursor indexes the rotation prefix, not the whole participant list — a participant still
     * waiting for the next Round isn't somewhere the cursor can point (see this class's javadoc). */
    private void requireValidTurnCursor(int currentIndex, int rotationSize) {
        int maxValidIndex = rotationSize - 1;
        if (currentIndex < -1 || currentIndex > maxValidIndex) {
            throw new IllegalArgumentException(
                    "currentIndex must be between -1 and " + maxValidIndex + " (participants in the turn rotation)");
        }
    }

    private SceneParticipantEntry toEntry(SceneParticipantRequest request) {
        return new SceneParticipantEntry(
                request.characterSheetId(),
                request.initiativeValue(),
                request.group(),
                new GridPosition(request.position().x(), request.position().y()),
                request.joinedAtRound(),
                null);
    }

    private SceneDocument findOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Scene not found: " + id));
    }

    private SceneResponse toResponse(SceneDocument document) {
        List<SceneParticipantResponse> participants = document.getParticipants().stream()
                .map(this::toParticipantResponse)
                .toList();
        List<SceneActionEvent> actionHistory = actionHistoryOf(document).stream()
                .map(this::toActionEvent)
                .toList();
        // Replayed on every read so a client joining late — or reconnecting mid-request — still
        // sees what the table was asked for, the same reason actionHistory is carried.
        List<RollRequestedEvent> rollRequests = rollRequestsOf(document).stream()
                .map(this::toRollRequestedEvent)
                .toList();
        List<RollRespondedEvent> rollResponses = rollResponsesOf(document).stream()
                .map(this::toRollRespondedEvent)
                .toList();
        List<AbilityActivatedEvent> abilityHistory = abilityHistoryOf(document).stream()
                .map(this::toAbilityEvent)
                .toList();
        return new SceneResponse(
                document.getId(),
                document.getName(),
                document.getTerrain(),
                participants,
                document.getCurrentRound(),
                document.getCurrentIndex(),
                document.isCombatScene(),
                document.isActive(),
                document.getImageUrl(),
                document.getWidth(),
                document.getHeight(),
                actionHistory,
                abilityHistory,
                rollRequests,
                rollResponses,
                document.getItemStoreMaxRarity(),
                resolveConnections(document),
                difficultTerrainOf(document).stream()
                        .map(cell -> new GridPositionDto(cell.x(), cell.y()))
                        .toList(),
                summonsOf(document),
                spawnersOf(document));
    }

    /** {@code null} on any document persisted before connections existed. */
    static Map<Direction, String> connectionsOf(SceneDocument document) {
        return document.getConnections() == null ? Map.of() : document.getConnections();
    }

    /**
     * Turns this scene's stored {@code Direction -> neighbour id} links into resolved neighbour
     * summaries — the lazy "id to Scene" step kept off the write path and done here instead. One
     * {@code findAllById} for the whole map (at most four ids); an id that no longer resolves still
     * appears, with a {@code null} name, rather than being dropped.
     */
    private Map<Direction, SceneConnectionResponse> resolveConnections(SceneDocument document) {
        Map<Direction, String> links = connectionsOf(document);
        if (links.isEmpty()) {
            return Map.of();
        }
        Map<String, SceneDocument> neighbours = new HashMap<>();
        repository.findAllById(links.values()).forEach(scene -> neighbours.put(scene.getId(), scene));

        Map<Direction, SceneConnectionResponse> resolved = new EnumMap<>(Direction.class);
        links.forEach((direction, neighbourId) -> {
            SceneDocument neighbour = neighbours.get(neighbourId);
            resolved.put(direction, new SceneConnectionResponse(
                    neighbourId,
                    neighbour == null ? null : neighbour.getName(),
                    neighbour != null && neighbour.isActive()));
        });
        return resolved;
    }

    private SceneParticipantResponse toParticipantResponse(SceneParticipantEntry entry) {
        return new SceneParticipantResponse(
                entry.characterSheetId(),
                entry.initiativeValue(),
                entry.group(),
                new GridPositionDto(entry.position().x(), entry.position().y()),
                entry.joinedAtRound(),
                toConcealmentDto(entry.concealment()),
                entry.initiativeOverride() == null ? null : entry.initiativeOverride().value(),
                entry.initiativeOverride() == null ? null : entry.initiativeOverride().rodadas(),
                entry.initiativeOverride() == null ? null : entry.initiativeOverride().started());
    }

    private static SceneConcealmentEntry toConcealmentEntry(ConcealmentDto dto) {
        return dto == null ? null : new SceneConcealmentEntry(dto.difficultyLevel(), dto.bonus(),
                dto.ordinaryConcealmentValue(), dto.expertConcealmentValue());
    }

    private static ConcealmentDto toConcealmentDto(SceneConcealmentEntry entry) {
        return entry == null ? null : new ConcealmentDto(entry.difficultyLevel(), entry.bonus(),
                entry.ordinaryConcealmentValue(), entry.expertConcealmentValue());
    }
}
