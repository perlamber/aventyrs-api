package org.aventyrs.api.scene;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.aventyrs.api.scene.dto.AddParticipantRequest;
import org.aventyrs.api.scene.dto.SceneCreateRequest;
import org.aventyrs.api.scene.dto.SceneGroupResponse;
import org.aventyrs.api.scene.dto.SceneParticipantResponse;
import org.aventyrs.api.scene.dto.SceneResponse;
import org.aventyrs.api.scene.dto.SceneUpdateRequest;
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
    private final SimpMessagingTemplate messagingTemplate;

    public SceneController(SceneService service, SimpMessagingTemplate messagingTemplate) {
        this.service = service;
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
