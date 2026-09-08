package org.aventyrs.api.scene;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.aventyrs.api.scene.dto.AddParticipantRequest;
import org.aventyrs.api.scene.dto.SceneActivatedEvent;
import org.aventyrs.api.scene.dto.SceneCombatStartedEvent;
import org.aventyrs.api.scene.dto.SceneCreateRequest;
import org.aventyrs.api.scene.dto.SceneGroupResponse;
import org.aventyrs.api.scene.dto.SceneParticipantResponse;
import org.aventyrs.api.scene.dto.SceneResponse;
import org.aventyrs.api.scene.dto.SceneUpdateRequest;
import org.aventyrs.core.scene.Direction;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scenes")
@Tag(name = "Scenes")
public class SceneController {

    private final SceneService service;
    private final SceneActivationService activationService;
    private final SceneConnectionService connectionService;
    private final SimpMessagingTemplate messagingTemplate;

    public SceneController(SceneService service, SceneActivationService activationService,
            SceneConnectionService connectionService, SimpMessagingTemplate messagingTemplate) {
        this.service = service;
        this.activationService = activationService;
        this.connectionService = connectionService;
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping
    public ResponseEntity<SceneResponse> create(@Valid @RequestBody SceneCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping("/available")
    public SceneResponse getAvailable() {
        return service.getAvailable();
    }

    @GetMapping("/{id}")
    public SceneResponse get(@PathVariable String id) {
        return service.get(id);
    }

    @GetMapping("/{id}/groups")
    public List<SceneGroupResponse> listGroups(@PathVariable String id) {
        return service.listGroups(id);
    }

    @GetMapping
    public List<SceneResponse> list() {
        return service.list();
    }

    @PostMapping("/{id}/participants")
    public ResponseEntity<SceneParticipantResponse> addParticipant(
            @PathVariable String id, @Valid @RequestBody AddParticipantRequest request) {
        SceneParticipantResponse added = service.addParticipant(id, request);
        broadcastRoster(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(added);
    }

    @DeleteMapping("/{id}/participants/{characterSheetId}")
    public ResponseEntity<Void> removeParticipant(@PathVariable String id, @PathVariable String characterSheetId) {
        service.removeParticipant(id, characterSheetId);
        broadcastRoster(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    public SceneResponse update(@PathVariable String id, @Valid @RequestBody SceneUpdateRequest request) {
        SceneResponse updated = service.update(id, request);
        broadcastRoster(updated);
        return updated;
    }

    /**
     * Combat breaks out in this scene — flips {@code combatScene} on (see {@link
     * SceneService#startCombat}, mirroring core 0.0.32's {@code Scene#startCombat()}) and, like a
     * roster change, re-broadcasts the whole scene plus a {@code /topic/scenes/{id}/combat} event
     * so every client turns into a combat scene together. A scene already in combat is a {@code
     * 409} (core's {@code SCENE_ALREADY_IN_COMBAT}). The full {@code PUT} still sets {@code
     * combatScene} straight through, for a scene rebuilt from persistence already mid-combat.
     */
    @PostMapping("/{id}/combat")
    public SceneResponse startCombat(@PathVariable String id) {
        SceneResponse scene = service.startCombat(id);
        messagingTemplate.convertAndSend(
                "/topic/scenes/" + id + "/combat",
                new SceneCombatStartedEvent(scene.combatScene(), scene.currentRound()));
        broadcastRoster(scene);
        return scene;
    }

    /**
     * Makes this the table's active scene and clears {@code active} on every other scene in the same
     * call (see {@link SceneActivationService}). {@code GET /scenes/available} returns the active
     * scene when there is exactly one. Persisting a scene never activates it; this is the only way.
     * A {@code 404} if the id is unknown.
     */
    @PutMapping("/{id}/active")
    public SceneResponse activate(@PathVariable String id) {
        return activationService.activate(id);
    }

    /**
     * Replaces this scene's connection map and mirrors every change onto the neighbours it touches,
     * atomically (see {@link SceneConnectionService#setConnections}). The body is a {@code Direction
     * -> neighbour scene id} object, e.g. {@code {"NORTH": "abc-123"}}; {@code {}} clears every
     * connection. A missing neighbour id is a {@code 404}; pointing a direction back at this scene is
     * a {@code 400}. The response's {@code connections} come back resolved to neighbour summaries.
     */
    @PutMapping("/{id}/connections")
    public SceneResponse setConnections(@PathVariable String id, @RequestBody Map<Direction, String> connections) {
        return connectionService.setConnections(id, connections);
    }

    /**
     * Travels one step out of this scene: the scene it connects to along {@code direction} becomes
     * the active scene and every other scene — this one included — is cleared, in one transaction
     * (see {@link SceneConnectionService#travel}). A {@code 404} if there's no connection that way,
     * the neighbour has been deleted, or {@code direction} isn't one of {@code NORTH/SOUTH/EAST/WEST}.
     * Returns the newly active scene.
     *
     * <p>Announces the step on {@code /topic/scenes/{id}/navigate} — the <em>origin</em> scene's
     * topic, the one the travelling clients are still subscribed to — so every client standing in
     * this scene follows the party to the neighbour. Deliberately not fired by {@link #activate}:
     * the console's "Ativar" only redirects fresh joins, travelling moves the whole table.
     */
    @PostMapping("/{id}/move/{direction}")
    public SceneResponse move(@PathVariable String id, @PathVariable Direction direction) {
        SceneResponse arrived = connectionService.travel(id, direction);
        messagingTemplate.convertAndSend(
                "/topic/scenes/" + id + "/navigate",
                new SceneActivatedEvent(arrived.id(), arrived.name()));
        return arrived;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Re-reads the scene to broadcast it — the mutations above return only what they changed
     * (one participant, or nothing at all), and what the topic carries is the whole scene. */
    private void broadcastRoster(String sceneId) {
        broadcastRoster(service.get(sceneId));
    }

    /**
     * Announces this scene's roster on {@code /topic/scenes/{sceneId}/participants}, so a client
     * already standing in the scene finds out that somebody joined it, left it, or was placed in
     * it by the GM. Everything else about a live scene already reaches the other clients over
     * STOMP (see {@link SceneRealtimeController}); joining is the one mutation that arrives by
     * REST, and until this existed a newcomer stayed invisible in every initiative panel but
     * their own until each client happened to reload the scene.
     *
     * <p>The payload is the whole {@link SceneResponse} rather than the one participant that
     * changed, because a roster change is not only a roster change: {@code removeParticipant}
     * moves the turn cursor to keep it on the same combatant, and a joiner's {@code
     * joinedAtRound} decides whether it enters the rotation now or at the next Round. Sending the
     * scene as the server holds it lets each client rebuild its rotation from server truth
     * instead of reproducing those rules against a delta.
     */
    private void broadcastRoster(SceneResponse scene) {
        messagingTemplate.convertAndSend("/topic/scenes/" + scene.id() + "/participants", scene);
    }
}
