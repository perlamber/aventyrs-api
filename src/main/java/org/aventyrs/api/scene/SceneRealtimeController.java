package org.aventyrs.api.scene;

import java.time.Instant;
import org.aventyrs.api.scene.dto.CharacterStatusChangedEvent;
import org.aventyrs.api.scene.dto.CharacterStatusMessage;
import org.aventyrs.api.scene.dto.GridPositionDto;
import org.aventyrs.api.scene.dto.GridResizeMessage;
import org.aventyrs.api.scene.dto.GridResizedEvent;
import org.aventyrs.api.scene.dto.ScenePingEvent;
import org.aventyrs.api.scene.dto.ScenePingMessage;
import org.aventyrs.api.scene.dto.TokenMoveMessage;
import org.aventyrs.api.scene.dto.TokenMovedEvent;
import org.aventyrs.api.scene.dto.TurnAdvancedEvent;
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
 * a turn advance ({@code /app/scenes/{sceneId}/turn}, persisted via {@link
 * SceneService#advanceTurn}), a board resize ({@code /app/scenes/{sceneId}/grid}, persisted via
 * {@link SceneService#resizeGrid}), and a transient, unpersisted "sonar" ping ({@code
 * /app/scenes/{sceneId}/ping}) — all re-broadcast to every client subscribed to the scene's
 * {@code /topic/scenes/{sceneId}/moves} / {@code .../status} / {@code .../turn} / {@code
 * .../grid} / {@code .../pings} destinations.
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

    /**
     * The Turn passed to whoever is next in Iniciativa order. The cursor is advanced and persisted
     * here so every client agrees on it (see {@link SceneService#advanceTurn}); each client then
     * runs its own {@code Scene#next()} off this broadcast, which is where the per-participant
     * turn lifecycle actually fires — this server holds no {@code CombatantSheet}s to fire it on.
     *
     * <p>Takes no payload: "next" is the whole request, and who is next is not the caller's to
     * assert. Rejected the same silent way {@link #move} is (an empty scene is the only way to get
     * here without a rotation to advance), leaving every client's panel on the turn it last saw.
     */
    @MessageMapping("/scenes/{sceneId}/turn")
    public void advanceTurn(@DestinationVariable String sceneId) {
        try {
            TurnAdvancedEvent event = sceneService.advanceTurn(sceneId);
            messagingTemplate.convertAndSend("/topic/scenes/" + sceneId + "/turn", event);
        } catch (RuntimeException ex) {
            log.warn("Rejected turn advance in scene {}: {}", sceneId, ex.getMessage());
        }
    }

    /**
     * The board was resized — persisted onto the scene ({@link SceneService#resizeGrid}), then
     * broadcast so every client redraws at the same extent instead of each holding its own idea of
     * how big the map is.
     *
     * <p>Only the GM's client offers the control, but with no auth in this API that's a client-side
     * restriction, not one this endpoint can enforce; it's noted here so the gap is visible when
     * auth does arrive. Rejected the same silent way {@link #move} is — a shrink that would strand
     * a token is refused, and every client keeps drawing the extent it already had.
     */
    @MessageMapping("/scenes/{sceneId}/grid")
    public void resizeGrid(@DestinationVariable String sceneId, @Payload GridResizeMessage message) {
        try {
            GridResizedEvent event = sceneService.resizeGrid(sceneId, message.width(), message.height());
            messagingTemplate.convertAndSend("/topic/scenes/" + sceneId + "/grid", event);
        } catch (RuntimeException ex) {
            log.warn("Rejected grid resize in scene {} to {}x{}: {}",
                    sceneId, message.width(), message.height(), ex.getMessage());
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
