package org.aventyrs.api.scene;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.aventyrs.api.common.NotFoundException;
import org.aventyrs.api.scene.dto.AddParticipantRequest;
import org.aventyrs.api.scene.dto.SceneResponse;
import org.aventyrs.core.scene.Direction;
import org.aventyrs.core.scene.grid.GridPosition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the two-sided invariant on {@code SceneDocument#connections}: every link is mirrored on the
 * neighbour along {@link Direction#opposite()}, and re-pointing or clearing one side always fixes
 * the other.
 *
 * <p>Core's {@code Scene#setConnection} / {@code Scene#removeConnection} are single-sided by design
 * — they record one direction on one Scene and never look at the neighbour. This service is the one
 * place that pairs them, and it runs the whole reconcile ({@code sceneId}, every neighbour it links
 * to or unlinks from, and any third scene a re-point displaces) inside a single transaction, so a
 * link can never be left half-formed.
 *
 * <p>Resolving a neighbour id back to a scene is deliberately <em>not</em> done here — that's the
 * read path's job ({@code SceneService#toResponse}). This side only moves ids around.
 */
@Service
public class SceneConnectionService {

    private final SceneRepository repository;
    private final SceneService sceneService;
    private final SceneActivationService activationService;

    public SceneConnectionService(SceneRepository repository, SceneService sceneService,
            SceneActivationService activationService) {
        this.repository = repository;
        this.sceneService = sceneService;
        this.activationService = activationService;
    }

    /**
     * Replaces {@code sceneId}'s whole connection map with {@code desired} and mirrors every change
     * onto the scenes it affects: a new link adds the opposite-facing link on the neighbour, a
     * dropped link clears it, and re-pointing a direction detaches whatever used to sit there — both
     * this scene's former neighbour on that side and, if the new neighbour's opposite side was
     * already taken, that third scene. The whole set of writes is one transaction.
     *
     * @throws NotFoundException        if the scene, or any neighbour named in {@code desired}, does
     *                                  not exist
     * @throws IllegalArgumentException if {@code desired} points a direction back at {@code sceneId}
     */
    @Transactional
    public SceneResponse setConnections(String sceneId, Map<Direction, String> desired) {
        Map<Direction, String> target = desired == null ? Map.of() : desired;
        target.forEach((direction, neighbourId) -> {
            if (sceneId.equals(neighbourId)) {
                throw new IllegalArgumentException("A scene cannot connect to itself: " + direction);
            }
        });

        // One object per scene id, so a scene reached twice (both an old and a new neighbour, say)
        // is mutated in place and written exactly once by the saveAll at the end.
        Map<String, SceneDocument> loaded = new HashMap<>();
        SceneDocument scene = require(loaded, sceneId);

        // Detach every current direction that's disappearing or being re-pointed.
        connectionsOf(scene).forEach((direction, oldNeighbourId) -> {
            if (!oldNeighbourId.equals(target.get(direction))) {
                clearBackLink(loaded, oldNeighbourId, direction.opposite(), sceneId);
            }
        });

        Map<Direction, String> next = new EnumMap<>(Direction.class);
        target.forEach((direction, neighbourId) -> {
            SceneDocument neighbour = require(loaded, neighbourId);
            String displaced = connectionsOf(neighbour).get(direction.opposite());
            if (displaced != null && !displaced.equals(sceneId)) {
                clearBackLink(loaded, displaced, direction, neighbourId);
            }
            putLink(neighbour, direction.opposite(), sceneId);
            next.put(direction, neighbourId);
        });
        scene.setConnections(next);

        repository.saveAll(loaded.values());
        return sceneService.get(sceneId);
    }

    /** What a {@link #travel} step did: the scene now active, and whether any participant actually
     * changed scenes (so the caller knows whether to re-broadcast both rosters). */
    public record TravelResult(SceneResponse arrived, boolean carriedAnyone) {
    }

    /**
     * One step of scene-to-scene travel: carries the chosen participant groups from {@code sceneId}
     * into the scene it connects to along {@code direction}, then makes that neighbour the active
     * scene and clears {@code active} everywhere else (delegated to {@link
     * SceneActivationService#activate}).
     *
     * <p>Every participant whose {@code group} is in {@code carryGroups} is removed from the origin
     * scene and added to the destination at a server-assigned cell — reusing {@link
     * SceneService#removeParticipant}/{@link SceneService#addParticipant} so the turn-cursor and
     * rotation bookkeeping stays correct on both ends. A traveller already present in the
     * destination (the same CharacterSheet was placed there directly) is left as it stands rather
     * than duplicated. Groups not listed stay behind in the origin scene. The whole transfer plus
     * the activation is one transaction.
     *
     * @throws NotFoundException if {@code sceneId} has no connection that way, or that neighbour no
     *                           longer exists
     */
    @Transactional
    public TravelResult travel(String sceneId, Direction direction, Set<UUID> carryGroups) {
        SceneDocument scene = repository.findById(sceneId)
                .orElseThrow(() -> new NotFoundException("Scene not found: " + sceneId));
        String neighbourId = connectionsOf(scene).get(direction);
        if (neighbourId == null) {
            throw new NotFoundException("Scene " + sceneId + " has no connection to the " + direction);
        }
        SceneDocument destination = repository.findById(neighbourId)
                .orElseThrow(() -> new NotFoundException("Scene not found: " + neighbourId));

        Set<UUID> groups = carryGroups == null ? Set.of() : carryGroups;
        List<SceneParticipantEntry> travellers = groups.isEmpty() ? List.of()
                : scene.getParticipants().stream()
                        .filter(participant -> groups.contains(participant.group()))
                        .toList();

        Set<String> alreadyAtDestination = new HashSet<>();
        destination.getParticipants().forEach(p -> alreadyAtDestination.add(p.characterSheetId()));

        // Where each newly-added traveller stood in the origin scene, so the destination can line
        // them up against the edge they walk in through instead of dumping them at cell (0,0).
        Map<String, GridPosition> arrivals = new LinkedHashMap<>();
        for (SceneParticipantEntry traveller : travellers) {
            sceneService.removeParticipant(sceneId, traveller.characterSheetId());
            if (!alreadyAtDestination.contains(traveller.characterSheetId())) {
                sceneService.addParticipant(neighbourId, new AddParticipantRequest(
                        traveller.characterSheetId(), traveller.initiativeValue(), traveller.group()));
                arrivals.put(traveller.characterSheetId(), traveller.position());
            }
        }
        sceneService.arrangeArrivals(neighbourId, arrivals, direction);

        return new TravelResult(activationService.activate(neighbourId), !travellers.isEmpty());
    }

    /** No-carry travel — kept for callers that only activate the neighbour. */
    public SceneResponse travel(String sceneId, Direction direction) {
        return travel(sceneId, direction, Set.of()).arrived();
    }

    /** Removes {@code direction} from {@code holderId}'s map, but only if it still points at {@code
     * expected} — guards against clobbering a link the neighbour has meanwhile re-pointed elsewhere.
     * A {@code holderId} that no longer resolves is a dangling link with nothing to fix. */
    private void clearBackLink(Map<String, SceneDocument> loaded, String holderId, Direction direction,
            String expected) {
        SceneDocument holder = loaded.computeIfAbsent(holderId, id -> repository.findById(id).orElse(null));
        if (holder == null) {
            return;
        }
        if (expected.equals(connectionsOf(holder).get(direction))) {
            Map<Direction, String> links = new EnumMap<>(connectionsOf(holder));
            links.remove(direction);
            holder.setConnections(links);
        }
    }

    private static void putLink(SceneDocument scene, Direction direction, String neighbourId) {
        Map<Direction, String> links = scene.getConnections() == null || scene.getConnections().isEmpty()
                ? new EnumMap<>(Direction.class)
                : new EnumMap<>(connectionsOf(scene));
        links.put(direction, neighbourId);
        scene.setConnections(links);
    }

    private SceneDocument require(Map<String, SceneDocument> loaded, String id) {
        SceneDocument scene = loaded.computeIfAbsent(id, key -> repository.findById(key).orElse(null));
        if (scene == null) {
            throw new NotFoundException("Scene not found: " + id);
        }
        return scene;
    }

    private static Map<Direction, String> connectionsOf(SceneDocument scene) {
        return SceneService.connectionsOf(scene);
    }
}
