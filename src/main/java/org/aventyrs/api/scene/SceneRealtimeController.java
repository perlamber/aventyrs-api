package org.aventyrs.api.scene;

import java.time.Instant;
import org.aventyrs.api.scene.dto.GridPositionDto;
import org.aventyrs.api.scene.dto.ScenePingEvent;
import org.aventyrs.api.scene.dto.ScenePingMessage;
import org.aventyrs.api.scene.dto.TokenMoveMessage;
import org.aventyrs.api.scene.dto.TokenMovedEvent;
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
 * via {@link SceneService#moveParticipant}) and a transient, unpersisted "sonar" ping ({@code
 * /app/scenes/{sceneId}/ping}) — both re-broadcast to every client subscribed to the scene's
 * {@code /topic/scenes/{sceneId}/moves} / {@code .../pings} destinations.
 *
 * <p>There's no auth in this API yet, so a rejected move (unknown participant, target cell already
 * occupied) has no client to report back to individually — it's logged and simply not broadcast,
 * leaving the requester's own token wherever it already was.
 */
@Controller
public class SceneRealtimeController {

    private static final Logger log = LoggerFactory.getLogger(SceneRealtimeController.class);

    private final SceneService sceneService;
    private final SimpMessagingTemplate messagingTemplate;

    public SceneRealtimeController(SceneService sceneService, SimpMessagingTemplate messagingTemplate) {
        this.sceneService = sceneService;
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
