package org.aventyrs.api.scene;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.aventyrs.api.common.NotFoundException;
import org.aventyrs.api.scene.dto.AddParticipantRequest;
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

    public SceneService(SceneRepository repository, CharacterSheetRepository characterSheetRepository,
            MonsterSheetRepository monsterSheetRepository, MongoTemplate mongoTemplate) {
        this.monsterSheetRepository = monsterSheetRepository;
        this.repository = repository;
        this.characterSheetRepository = characterSheetRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public SceneResponse create(SceneCreateRequest request) {
        SceneDocument document = new SceneDocument(
                UUID.randomUUID().toString(), request.name(), TerrainType.valueOf(request.terrain()), List.of(), 0, -1,
                false, false, Map.of(), null, null, request.width(), request.height(), Instant.now(), List.of(),
                List.of(), List.of());
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

        List<SceneParticipantEntry> participants = request.participants().stream()
                .map(this::toEntry)
                .toList();
        requireParticipantsExist(participants);
        requireDistinctPositions(participants);
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
                joinsNow ? document.getCurrentRound() : document.getCurrentRound() + 1);
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
        repository.save(document);
    }

    public SceneParticipantEntry moveParticipant(String id, String characterSheetId, GridPosition newPosition) {
        SceneDocument document = findOrThrow(id);

        List<SceneParticipantEntry> participants = new ArrayList<>(document.getParticipants());
        int index = indexOfParticipant(participants, characterSheetId);
        SceneParticipantEntry moved = new SceneParticipantEntry(
                characterSheetId,
                participants.get(index).initiativeValue(),
                participants.get(index).group(),
                newPosition,
                participants.get(index).joinedAtRound());
        participants.set(index, moved);
        requireDistinctPositions(participants);

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
                participants.set(i, new SceneParticipantEntry(entry.characterSheetId(),
                        entry.initiativeValue(), entry.group(), position, entry.joinedAtRound()));
            }
        }
        requireDistinctPositions(participants);
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
        repository.save(document);

        return new GridResizedEvent(width, height);
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
        SceneDocument document = findOrThrow(id);

        List<SceneParticipantEntry> participants = new ArrayList<>(document.getParticipants());
        int round = document.getCurrentRound();
        int rotationSize = rotationSize(participants, round);
        if (rotationSize == 0) {
            throw new IllegalArgumentException("No participants in scene: " + id);
        }

        int index = document.getCurrentIndex() + 1;
        if (index >= rotationSize) {
            index = 0;
            if (document.isCombatScene()) {
                round++;
                participants = mergeAndSortRotation(participants, round);
            }
        }

        document.setParticipants(participants);
        document.setCurrentRound(round);
        document.setCurrentIndex(index);
        repository.save(document);

        return new TurnAdvancedEvent(participants.get(index).characterSheetId(), round, index);
    }

    /**
     * The Round-boundary bookkeeping {@link #advanceTurn} runs on every wrap, mirroring {@code
     * Scene#startNewRound()}: everyone whose {@code joinedAtRound} has now come round joins the
     * rotation prefix, which is then re-sorted by {@code initiativeValue} descending. The sort is
     * stable, so ties keep the order they already had — including a joiner tying with someone
     * already there, which is the same tie behavior {@link #rotationInsertionIndex} preserves.
     */
    private List<SceneParticipantEntry> mergeAndSortRotation(List<SceneParticipantEntry> participants, int round) {
        List<SceneParticipantEntry> rotation = new ArrayList<>(participants.stream()
                .filter(entry -> entry.joinedAtRound() <= round)
                .toList());
        rotation.sort(Comparator.comparingInt(SceneParticipantEntry::initiativeValue).reversed());

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
                toOutcome(message));

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
                Instant.now());

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
                Instant.now());

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
                entry.targetCharacterSheetIds(), entry.prompt(), entry.requestedAt());
    }

    private RollRespondedEvent toRollRespondedEvent(SceneRollResponseEntry entry) {
        return new RollRespondedEvent(entry.requestId(), entry.characterSheetId(), entry.kind(),
                entry.dice(), entry.succeeded(), entry.margin(), entry.total(), entry.requiredTotal(),
                entry.note(), entry.respondedAt());
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
                outcome == null ? null : outcome.reachedDifficultyLevel());
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
            if (participants.get(i).initiativeValue() < entry.initiativeValue()) {
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
    public void requireParticipant(String id, String characterSheetId) {
        indexOfParticipant(findOrThrow(id).getParticipants(), characterSheetId);
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

    private void requireDistinctPositions(List<SceneParticipantEntry> participants) {
        long distinctPositions = participants.stream().map(SceneParticipantEntry::position).distinct().count();
        if (distinctPositions != participants.size()) {
            throw new IllegalArgumentException("Two participants cannot occupy the same grid position");
        }
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
                request.joinedAtRound());
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
                rollRequests,
                rollResponses,
                document.getItemStoreMaxRarity(),
                resolveConnections(document));
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
                entry.joinedAtRound());
    }
}
