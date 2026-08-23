package org.aventyrs.api.scene;

import java.time.Instant;
import org.aventyrs.api.scene.dto.CharacterStatusChangedEvent;
import org.aventyrs.api.scene.dto.CharacterStatusMessage;
import org.aventyrs.api.scene.dto.GridPositionDto;
import org.aventyrs.api.scene.dto.ScenePingEvent;
import org.aventyrs.api.scene.dto.ScenePingMessage;
import org.aventyrs.api.scene.dto.TokenMoveMessage;
import org.aventyrs.api.scene.dto.TokenMovedEvent;
import org.aventyrs.api.sheet.CharacterSheetService;
import org.aventyrs.core.scene.grid.GridPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * Live Scene events over STOMP: a participant move ({@code /app/scenes/{sceneId}/move}, persisted
 * via {@link SceneService#moveParticipant}), a combat-state change ({@code
 * /app/scenes/{sceneId}/status}, persisted via {@link CharacterSheetService#updateCombatStatus}),
 * and a transient, unpersisted "sonar" ping ({@code /app/scenes/{sceneId}/ping}) — all
 * re-broadcast to every client subscribed to the scene's {@code /topic/scenes/{sceneId}/moves} /
 * {@code .../status} / {@code .../pings} destinations.
 *
 * <p>There's no auth in this API yet, so a rejected move (unknown participant, target cell already
 * occupied) has no client to report back to individually — it's logged and simply not broadcast,
 * leaving the requester's own token wherever it already was. A rejected status change is handled
 * the same way, leaving every other client's badge on the tier it last saw.
 */
@Controller
public class SceneRealtimeController {

    private static final Logger log = LoggerFactory.getLogger(SceneRealtimeController.class);

    private final SceneService sceneService;
    private final CharacterSheetService characterSheetService;
    private final SimpMessagingTemplate messagingTemplate;

    public SceneRealtimeController(SceneService sceneService, CharacterSheetService characterSheetService,
            SimpMessagingTemplate messagingTemplate) {
        this.sceneService = sceneService;
        this.characterSheetService = characterSheetService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/scenes/{sceneId}/move")
    public void move(@DestinationVariable String sceneId, @Payload TokenMoveMessage message) {
        try {
            SceneParticipantEntry updated = sceneService.moveParticipant(
                    sceneId, message.characterSheetId(), toGridPosition(message.position()));
            messagingTemplate.convertAndSend(
                    "/topic/scenes/" + sceneId + "/moves",
                    new TokenMovedEvent(updated.characterSheetId(), toDto(updated.position())));
        } catch (RuntimeException ex) {
            log.warn("Rejected move in scene {} for participant {}: {}",
                    sceneId, message.characterSheetId(), ex.getMessage());
        }
    }

    /**
     * A participant's damage/{@link org.aventyrs.core.character.CharacterStatus} tier changed —
     * persisted onto their character sheet, then broadcast so every client watching the scene can
     * repaint that token's status badge without re-fetching the sheet.
     *
     * <p>Unlike {@link #move}, the write target is the character sheet rather than the scene
     * document, so scene membership has to be asserted explicitly ({@link
     * SceneService#requireParticipant}) before touching it — otherwise this topic would let any
     * client rewrite the combat state of any sheet in the database.
     */
    @MessageMapping("/scenes/{sceneId}/status")
    public void status(@DestinationVariable String sceneId, @Payload CharacterStatusMessage message) {
        try {
            sceneService.requireParticipant(sceneId, message.characterSheetId());
            characterSheetService.updateCombatStatus(
                    message.characterSheetId(), message.hitPointsSpent(), message.status());
            messagingTemplate.convertAndSend(
                    "/topic/scenes/" + sceneId + "/status",
                    new CharacterStatusChangedEvent(
                            message.characterSheetId(), message.hitPointsSpent(), message.status()));
        } catch (RuntimeException ex) {
            log.warn("Rejected status change in scene {} for participant {}: {}",
                    sceneId, message.characterSheetId(), ex.getMessage());
        }
    }

    @MessageMapping("/scenes/{sceneId}/ping")
    public void ping(@DestinationVariable String sceneId, @Payload ScenePingMessage message) {
        messagingTemplate.convertAndSend(
                "/topic/scenes/" + sceneId + "/pings",
                new ScenePingEvent(message.position(), Instant.now()));
    }

    private static GridPosition toGridPosition(GridPositionDto dto) {
        return new GridPosition(dto.x(), dto.y());
    }

    private static GridPositionDto toDto(GridPosition position) {
        return new GridPositionDto(position.x(), position.y());
    }
}
