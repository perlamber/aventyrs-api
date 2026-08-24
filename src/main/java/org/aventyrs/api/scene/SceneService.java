package org.aventyrs.api.scene;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.aventyrs.api.common.NotFoundException;
import org.aventyrs.api.scene.dto.AddParticipantRequest;
import org.aventyrs.api.scene.dto.GridPositionDto;
import org.aventyrs.api.scene.dto.SceneCreateRequest;
import org.aventyrs.api.scene.dto.SceneGroupResponse;
import org.aventyrs.api.scene.dto.SceneParticipantRequest;
import org.aventyrs.api.scene.dto.SceneParticipantResponse;
import org.aventyrs.api.scene.dto.SceneResponse;
import org.aventyrs.api.scene.dto.SceneUpdateRequest;
import org.aventyrs.api.scene.dto.TurnAdvancedEvent;
import org.aventyrs.api.sheet.CharacterSheetRepository;
import org.aventyrs.core.scene.TerrainType;
import org.aventyrs.core.scene.grid.GridPosition;
import org.springframework.stereotype.Service;

/**
 * CRUD plus the one bit of action-time behavior this API does arbitrate: {@link #advanceTurn}.
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

    public SceneService(SceneRepository repository, CharacterSheetRepository characterSheetRepository) {
        this.repository = repository;
        this.characterSheetRepository = characterSheetRepository;
    }

    public SceneResponse create(SceneCreateRequest request) {
        SceneDocument document = new SceneDocument(
                UUID.randomUUID().toString(), request.name(), TerrainType.valueOf(request.terrain()), List.of(), 0, -1,
                false, null, request.width(), request.height(), Instant.now());
        return toResponse(repository.save(document));
    }

    public SceneResponse get(String id) {
        return toResponse(findOrThrow(id));
    }

    public SceneResponse getAvailable() {
        return repository.findTopByOrderByCreatedAtDesc()
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("No scenes available"));
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

        document.setName(request.name());
        document.setParticipants(participants);
        document.setCurrentRound(request.currentRound());
        document.setCurrentIndex(request.currentIndex());
        document.setCombatScene(request.combatScene());
        document.setImageUrl(request.imageUrl());

        return toResponse(repository.save(document));
    }

    public SceneParticipantResponse addParticipant(String id, AddParticipantRequest request) {
        SceneDocument document = findOrThrow(id);
        if (!characterSheetRepository.existsById(request.characterSheetId())) {
            throw new IllegalArgumentException("CharacterSheet not found: " + request.characterSheetId());
        }

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
     * Moves this scene's turn cursor on by one, mirroring {@code Scene#next()}'s arithmetic: step
     * to the next participant in the rotation, and on wrapping back to the top advance the Round,
     * merge whoever was waiting, and re-derive the order from everyone's {@code initiativeValue}.
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
            round++;
            participants = mergeAndSortRotation(participants, round);
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
            if (!characterSheetRepository.existsById(participant.characterSheetId())) {
                throw new IllegalArgumentException("CharacterSheet not found: " + participant.characterSheetId());
            }
        }
    }

    private void requireDistinctPositions(List<SceneParticipantEntry> participants) {
        long distinctPositions = participants.stream().map(SceneParticipantEntry::position).distinct().count();
        if (distinctPositions != participants.size()) {
            throw new IllegalArgumentException("Two participants cannot occupy the same grid position");
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
        return new SceneResponse(
                document.getId(),
                document.getName(),
                document.getTerrain(),
                participants,
                document.getCurrentRound(),
                document.getCurrentIndex(),
                document.isCombatScene(),
                document.getImageUrl(),
                document.getWidth(),
                document.getHeight());
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
