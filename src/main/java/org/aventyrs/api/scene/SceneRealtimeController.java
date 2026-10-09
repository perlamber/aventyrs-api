package org.aventyrs.api.scene;

import static org.aventyrs.api.analytics.AnalyticsEventType.ABILITY;
import static org.aventyrs.api.analytics.AnalyticsEventType.ACTION;
import static org.aventyrs.api.analytics.AnalyticsEventType.COMBATANT_STATE;
import static org.aventyrs.api.analytics.AnalyticsEventType.COMBAT_ENDED;
import static org.aventyrs.api.analytics.AnalyticsEventType.COMBAT_STARTED;
import static org.aventyrs.api.analytics.AnalyticsEventType.CONDITION;
import static org.aventyrs.api.analytics.AnalyticsEventType.DAMAGE;
import static org.aventyrs.api.analytics.AnalyticsEventType.HIDDEN;
import static org.aventyrs.api.analytics.AnalyticsEventType.INITIATIVE;
import static org.aventyrs.api.analytics.AnalyticsEventType.MOVE;
import static org.aventyrs.api.analytics.AnalyticsEventType.ROLL_REQUEST;
import static org.aventyrs.api.analytics.AnalyticsEventType.ROLL_RESPONSE;
import static org.aventyrs.api.analytics.AnalyticsEventType.SPELL_LANDED;
import static org.aventyrs.api.analytics.AnalyticsEventType.STATUS;
import static org.aventyrs.api.analytics.AnalyticsEventType.TIME;
import static org.aventyrs.api.analytics.AnalyticsEventType.TURN_ADVANCED;

import org.aventyrs.api.scene.dto.EgoGrantMessage;
import org.aventyrs.api.scene.dto.EgoGrantedEvent;
import org.aventyrs.api.scene.dto.InitiativeOverriddenEvent;
import org.aventyrs.api.scene.dto.InitiativeOverrideMessage;
import org.aventyrs.api.scene.dto.SceneTimeMessage;
import org.aventyrs.api.scene.dto.CombatantStateMessage;
import org.aventyrs.api.scene.dto.CombatantStateChangedEvent;
import java.time.Instant;
import org.aventyrs.api.scene.dto.AbilityActivatedEvent;
import org.aventyrs.api.scene.dto.AbilityActivationMessage;
import org.aventyrs.api.scene.dto.CharacterStatusChangedEvent;
import org.aventyrs.api.monster.MonsterSheetService;
import org.aventyrs.api.scene.dto.CharacterStatusMessage;
import org.aventyrs.api.scene.dto.GridPositionDto;
import org.aventyrs.api.scene.dto.GridResizeMessage;
import org.aventyrs.api.scene.dto.GridResizedEvent;
import org.aventyrs.api.scene.dto.ConditionChangeMessage;
import org.aventyrs.api.scene.dto.ConditionChangedEvent;
import org.aventyrs.api.scene.dto.HiddenStatusChangedEvent;
import org.aventyrs.api.scene.dto.HiddenStatusMessage;
import org.aventyrs.api.scene.dto.RollRequestMessage;
import org.aventyrs.api.scene.dto.RollRequestedEvent;
import org.aventyrs.api.scene.dto.RollResponseMessage;
import org.aventyrs.api.scene.dto.RollRespondedEvent;
import org.aventyrs.api.scene.dto.RecordActionMessage;
import org.aventyrs.api.scene.dto.SceneActionEvent;
import org.aventyrs.api.scene.dto.SceneCombatStartedEvent;
import org.aventyrs.api.scene.dto.ScenePingEvent;
import org.aventyrs.api.scene.dto.ScenePingMessage;
import org.aventyrs.api.scene.dto.SpellLandedMessage;
import org.aventyrs.api.scene.dto.TerrainPaintMessage;
import org.aventyrs.api.scene.dto.TerrainPaintedEvent;
import org.aventyrs.api.scene.dto.TokenMoveMessage;
import org.aventyrs.api.scene.dto.TokenMovedEvent;
import org.aventyrs.api.scene.dto.TurnAdvancedEvent;
import org.aventyrs.api.sheet.CharacterSheetService;
import org.aventyrs.core.scene.grid.GridPosition;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.aventyrs.api.analytics.AnalyticsRecorder;
import org.aventyrs.api.scene.dto.AttackHitMessage;
import org.aventyrs.api.scene.dto.DamageDealtMessage;
import org.aventyrs.api.scene.dto.TitleEffectsDto;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * Live Scene events over STOMP: a participant move ({@code /app/scenes/{sceneId}/move}, persisted
 * via {@link SceneService#moveParticipant}), a combat-state change ({@code
 * /app/scenes/{sceneId}/status}, persisted via {@link CharacterSheetService#updateCombatStatus}),
 * a turn advance ({@code /app/scenes/{sceneId}/turn}, persisted via {@link
 * SceneService#advanceTurn}), a combat start ({@code /app/scenes/{sceneId}/combat}, persisted via
 * {@link SceneService#startCombat}), a board resize ({@code /app/scenes/{sceneId}/grid}, persisted
 * via {@link SceneService#resizeGrid}), a recorded combat action ({@code
 * /app/scenes/{sceneId}/actions}, persisted via {@link SceneService#recordAction}), a transient,
 * unpersisted "sonar" ping ({@code /app/scenes/{sceneId}/ping}), and an equally transient
 * Esconder-se concealment flag ({@code /app/scenes/{sceneId}/hidden}, see {@link #hidden}) — all
 * re-broadcast to every client subscribed to the scene's {@code /topic/scenes/{sceneId}/moves} /
 * {@code .../status} / {@code .../turn} / {@code .../combat} / {@code .../grid} / {@code
 * .../actions} / {@code .../pings} / {@code .../hidden} destinations.
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
    private final MonsterSheetService monsterSheetService;
    private final SimpMessagingTemplate messagingTemplate;
    private final AnalyticsRecorder analytics;

    public SceneRealtimeController(SceneService sceneService, CharacterSheetService characterSheetService,
            MonsterSheetService monsterSheetService, SimpMessagingTemplate messagingTemplate,
            AnalyticsRecorder analytics) {
        this.sceneService = sceneService;
        this.characterSheetService = characterSheetService;
        this.monsterSheetService = monsterSheetService;
        this.messagingTemplate = messagingTemplate;
        this.analytics = analytics;
    }

    @MessageMapping("/scenes/{sceneId}/move")
    public void move(@DestinationVariable String sceneId, @Payload TokenMoveMessage message) {
        try {
            SceneParticipantEntry updated = sceneService.moveParticipant(
                    sceneId, message.characterSheetId(), toGridPosition(message.position()),
                    Boolean.TRUE.equals(message.sharesSpace()));
            messagingTemplate.convertAndSend(
                    "/topic/scenes/" + sceneId + "/moves",
                    new TokenMovedEvent(updated.characterSheetId(), toDto(updated.position())));
            analytics.record(MOVE, sceneId, message.characterSheetId(), null, message);
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
            // A participant id names a character sheet or a monster sheet, with no discriminator —
            // a foe's damage, and the PD its Habilidades Monstruosas spend, persist onto its own
            // document. Before this a foe's frame was rejected here and never reached anyone.
            if (monsterSheetService.exists(message.characterSheetId())) {
                monsterSheetService.updateCombatStatus(
                        message.characterSheetId(), message.hitPointsSpent(), message.magicPointsSpent(),
                        message.determinationPointsSpent(), message.temporaryEgoPoints());
                if (message.bleedingEffects() != null) {
                    monsterSheetService.updateBleeding(message.characterSheetId(), message.bleedingEffects());
                }
            } else {
                characterSheetService.updateCombatStatus(
                        message.characterSheetId(), message.hitPointsSpent(), message.magicPointsSpent(),
                        message.determinationPointsSpent(), message.status(), message.temporaryEgoPoints(),
                        message.hourlyEgoRecoveries(), message.exhausted(), message.lockedHitPoints(),
                        message.lifeStealLockedHitPoints(), message.restScopedUses(), message.egoLedger(),
                        message.restLockedHitPoints(), message.subordinates());
                if (message.bleedingEffects() != null) {
                    characterSheetService.updateBleeding(message.characterSheetId(), message.bleedingEffects());
                }
            }
            messagingTemplate.convertAndSend(
                    "/topic/scenes/" + sceneId + "/status",
                    new CharacterStatusChangedEvent(
                            message.characterSheetId(), message.hitPointsSpent(),
                            message.magicPointsSpent(), message.determinationPointsSpent(),
                            message.status(), message.bleedingEffects()));
            analytics.record(STATUS, sceneId, message.characterSheetId(), null, message);
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
            SceneService.TurnAdvance advance = sceneService.advanceTurnReporting(sceneId);
            // Invocations joining (a Totem's) or leaving (a spent Duração) change the roster too.
            if (advance.rosterChanged()) {
                messagingTemplate.convertAndSend("/topic/scenes/" + sceneId + "/participants",
                        sceneService.get(sceneId));
            }
            messagingTemplate.convertAndSend("/topic/scenes/" + sceneId + "/turn", advance.event());
            analytics.record(TURN_ADVANCED, sceneId, advance.event().characterSheetId(), null, advance.event());
        } catch (RuntimeException ex) {
            log.warn("Rejected turn advance in scene {}: {}", sceneId, ex.getMessage());
        }
    }

    /**
     * Combat broke out — {@code combatScene} is flipped on and persisted ({@link
     * SceneService#startCombat}, mirroring core 0.0.32's {@code Scene#startCombat()}), then broadcast
     * so every client turns its own scene into a combat scene and runs its own {@code
     * Scene#startCombat()} — which is where the start-of-combat Talento Blessings resolve, on the
     * live {@code CombatantSheet}s that exist only client-side (same split as {@link #advanceTurn}).
     *
     * <p>Takes no payload: "combat started" is the whole request. Rejected the same silent way
     * {@link #move} is — most often because the scene is already in combat ({@code
     * SCENE_ALREADY_IN_COMBAT}), which leaves every client's state untouched.
     */
    @MessageMapping("/scenes/{sceneId}/combat")
    public void startCombat(@DestinationVariable String sceneId) {
        try {
            var scene = sceneService.startCombat(sceneId);
            // The order was rebuilt from the top (SceneService#resetRotation); clients take it from the roster.
            messagingTemplate.convertAndSend("/topic/scenes/" + sceneId + "/participants", scene);
            SceneCombatStartedEvent event = new SceneCombatStartedEvent(scene.combatScene(), scene.currentRound());
            messagingTemplate.convertAndSend("/topic/scenes/" + sceneId + "/combat", event);
            analytics.record(COMBAT_STARTED, sceneId, null, null, event);
        } catch (RuntimeException ex) {
            log.warn("Rejected combat start in scene {}: {}", sceneId, ex.getMessage());
        }
    }

    /**
     * Combat ended — {@code combatScene} is flipped off and the Rodada reset ({@link
     * SceneService#endCombat}, mirroring core 0.0.48's {@code Scene#endCombat()}), then broadcast on
     * the same {@code /combat} topic as a start, with {@code combatScene} {@code false}, so every
     * client runs its own {@code Scene#endCombat()} and drops its combat-scoped grants. Takes no
     * payload; rejected silently (most often {@code SCENE_NOT_IN_COMBAT}), like {@link #startCombat}.
     */
    @MessageMapping("/scenes/{sceneId}/combat/end")
    public void endCombat(@DestinationVariable String sceneId) {
        try {
            var scene = sceneService.endCombat(sceneId);
            // The order was rebuilt from the top (SceneService#resetRotation); clients take it from the roster.
            messagingTemplate.convertAndSend("/topic/scenes/" + sceneId + "/participants", scene);
            SceneCombatStartedEvent event = new SceneCombatStartedEvent(scene.combatScene(), scene.currentRound());
            messagingTemplate.convertAndSend("/topic/scenes/" + sceneId + "/combat", event);
            analytics.record(COMBAT_ENDED, sceneId, null, null, event);
        } catch (RuntimeException ex) {
            log.warn("Rejected combat end in scene {}: {}", sceneId, ex.getMessage());
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
    /**
     * The GM painted or cleared Terreno Difícil — persisted ({@link SceneService#paintTerrain}), then
     * broadcast as the scene's whole difficult set so every client prices movement off the same
     * board. GM-only on the client, unenforced here, and rejected silently like {@link #move}.
     */
    @MessageMapping("/scenes/{sceneId}/terrain")
    public void paintTerrain(@DestinationVariable String sceneId, @Payload TerrainPaintMessage message) {
        try {
            List<GridPosition> cells = message.cells() == null ? List.of()
                    : message.cells().stream().map(SceneRealtimeController::toGridPosition).toList();
            TerrainPaintedEvent event = sceneService.paintTerrain(sceneId, cells, message.difficult());
            messagingTemplate.convertAndSend("/topic/scenes/" + sceneId + "/terrain", event);
        } catch (RuntimeException ex) {
            log.warn("Rejected terrain paint in scene {}: {}", sceneId, ex.getMessage());
        }
    }

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

    /**
     * One resolved {@code CombatantAction} the sending client already rolled — persisted onto this
     * scene's permanent combat log ({@link SceneService#recordAction}), then broadcast so every
     * client's log fills in the same entry rather than only the one that rolled it. Rejected the
     * same silent way {@link #move} is: an unknown participant, or an internally inconsistent
     * {@code ActionCost} (see {@link RecordActionMessage}), leaves every client's log as it was.
     */
    @MessageMapping("/scenes/{sceneId}/actions")
    public void recordAction(@DestinationVariable String sceneId, @Payload RecordActionMessage message) {
        try {
            SceneActionEvent event = sceneService.recordAction(sceneId, message);
            messagingTemplate.convertAndSend("/topic/scenes/" + sceneId + "/actions", event);
            analytics.record(ACTION, sceneId, event.characterSheetId(), actionTargetsOf(event), event);
        } catch (RuntimeException ex) {
            log.warn("Rejected action in scene {} for participant {}: {}",
                    sceneId, message.characterSheetId(), ex.getMessage());
        }
    }

    /**
     * The Narrador asks the table to roll — persisted and then broadcast to <b>everyone</b> in the
     * scene, not only to whoever must answer. That breadth is the point rather than a convenience:
     * a player whose character isn't the target may still want to react to an attack aimed at
     * somebody else's, and can only do so if they saw it.
     *
     * <p>Rejected the same silent way {@link #move} is — an unknown target leaves every client's
     * panel as it was. Nothing checks that the sender is the Narrador; see {@link
     * org.aventyrs.api.scene.dto.RollRequestMessage} for why that is not enforceable here.
     */
    /**
     * A Habilidade de Título one client just activated — persisted onto this Scene's log and
     * broadcast to <b>everyone</b>, not only to the activator.
     *
     * <p>That breadth is the whole point rather than a convenience: a clause granting "a você e
     * seus aliados adjacentes" cannot be resolved by the client that activated it, because those
     * allies' real sheets live in their own clients. Each recipient's client grants the reported
     * Blessings to the sheets it actually owns when this arrives.
     *
     * <p>Rejected the same silent way {@link #move} is — an unknown participant leaves every
     * client's log as it was.
     */
    @MessageMapping("/scenes/{sceneId}/abilities")
    public void recordAbility(@DestinationVariable String sceneId, @Payload AbilityActivationMessage message) {
        try {
            AbilityActivatedEvent event = sceneService.recordAbility(sceneId, message);
            messagingTemplate.convertAndSend("/topic/scenes/" + sceneId + "/abilities", event);
            analytics.record(ABILITY, sceneId, event.characterSheetId(), abilityTargetsOf(event), event);
        } catch (RuntimeException ex) {
            log.warn("Rejected ability activation in scene {} for participant {}: {}",
                    sceneId, message.characterSheetId(), ex.getMessage());
        }
    }

    @MessageMapping("/scenes/{sceneId}/roll-requests")
    public void requestRoll(@DestinationVariable String sceneId, @Payload RollRequestMessage message) {
        try {
            RollRequestedEvent event = sceneService.requestRoll(sceneId, message);
            messagingTemplate.convertAndSend("/topic/scenes/" + sceneId + "/roll-requests", event);
            analytics.record(ROLL_REQUEST, sceneId, event.attackerCharacterSheetId(), event.targetCharacterSheetIds(),
                    event);
        } catch (RuntimeException ex) {
            log.warn("Rejected roll request in scene {}: {}", sceneId, ex.getMessage());
        }
    }

    /**
     * One player's answer, persisted and broadcast so the whole table sees who answered and how.
     *
     * <p>Every client settles its own "already answered" state off this broadcast rather than
     * optimistically, so two clients cannot disagree about whether a request is still open.
     */
    @MessageMapping("/scenes/{sceneId}/roll-responses")
    public void respondToRoll(@DestinationVariable String sceneId, @Payload RollResponseMessage message) {
        try {
            RollRespondedEvent event = sceneService.respondToRoll(sceneId, message);
            messagingTemplate.convertAndSend("/topic/scenes/" + sceneId + "/roll-responses", event);
            analytics.record(ROLL_RESPONSE, sceneId, event.characterSheetId(), null, event);
        } catch (RuntimeException ex) {
            log.warn("Rejected roll response in scene {} from participant {}: {}",
                    sceneId, message.characterSheetId(), ex.getMessage());
        }
    }

    @MessageMapping("/scenes/{sceneId}/ping")
    public void ping(@DestinationVariable String sceneId, @Payload ScenePingMessage message) {
        messagingTemplate.convertAndSend(
                "/topic/scenes/" + sceneId + "/pings",
                new ScenePingEvent(message.position(), Instant.now()));
    }

    /**
     * A participant's Esconder-se concealment started or ended — persisted onto the participant
     * ({@link SceneService#setConcealment}), then relayed. The {@code Hidden} Condição itself still
     * lives on aventyrs-core's in-memory {@code CombatantSheet} the hiding client holds; what is
     * persisted is its GD, so a participant can <i>enter</i> a Cena hidden (see {@code
     * AddParticipantRequest#concealment}) and a late joiner — or the client controlling that
     * participant, after a reconnect — learns who is presently hidden from the Scene itself rather
     * than only from a live broadcast it may have missed.
     *
     * <p>Membership is still asserted ({@link SceneService#requireParticipant}), same reason {@link
     * #status} asserts it and {@link #ping} does not: this names one specific participant's state
     * rather than an untargeted board-wide marker, so an unknown id is rejected rather than handed
     * to every client's board.
     */
    @MessageMapping("/scenes/{sceneId}/hidden")
    public void hidden(@DestinationVariable String sceneId, @Payload HiddenStatusMessage message) {
        try {
            sceneService.setConcealment(sceneId, message.characterSheetId(), message.toConcealment());
            messagingTemplate.convertAndSend(
                    "/topic/scenes/" + sceneId + "/hidden",
                    new HiddenStatusChangedEvent(message.characterSheetId(), message.hidden(),
                            message.ordinaryConcealmentValue(), message.expertConcealmentValue(),
                            message.difficultyLevel(), message.bonus()));
            analytics.record(HIDDEN, sceneId, message.characterSheetId(), null, message);
        } catch (RuntimeException ex) {
            log.warn("Rejected hidden-status change in scene {} for participant {}: {}",
                    sceneId, message.characterSheetId(), ex.getMessage());
        }
    }

    /**
     * A Condição put on, or taken off, a participant another client owns (core 0.1.5) — an Agarrar, an
     * escape, a Desacordado. Relayed, never persisted: the owning client applies it to its own core sheet
     * and reports the result in its next {@link #combatantState} frame. Membership of the target (and of
     * the source, when named) is asserted, same reason {@link #hidden} checks it; a malformed message is
     * dropped.
     */
    @MessageMapping("/scenes/{sceneId}/conditions")
    public void conditionChanged(@DestinationVariable String sceneId, @Payload ConditionChangeMessage message) {
        if (message == null || message.targetCharacterSheetId() == null || message.conditionType() == null
                || message.op() == null) {
            return;
        }
        try {
            sceneService.requireParticipant(sceneId, message.targetCharacterSheetId());
            if (message.sourceCharacterSheetId() != null) {
                sceneService.requireParticipant(sceneId, message.sourceCharacterSheetId());
            }
            messagingTemplate.convertAndSend("/topic/scenes/" + sceneId + "/conditions",
                    ConditionChangedEvent.of(message));
            analytics.record(CONDITION, sceneId, message.sourceCharacterSheetId(),
                    List.of(message.targetCharacterSheetId()), message);
        } catch (RuntimeException ex) {
            log.warn("Rejected Condição change in scene {} for participant {}: {}",
                    sceneId, message.targetCharacterSheetId(), ex.getMessage());
        }
    }

    /**
     * An Iniciativa Ego point changing a participant's place in the order (core 0.0.79). Persisted, because this
     * server owns the order ({@link SceneService#advanceTurn}), and broadcast so every board shows it. Membership
     * is asserted by the lookup. Rejected silently like {@link #hidden}.
     */
    @MessageMapping("/scenes/{sceneId}/initiative")
    public void initiative(@DestinationVariable String sceneId, @Payload InitiativeOverrideMessage message) {
        try {
            sceneService.setInitiativeOverride(sceneId, message.characterSheetId(), message.value(), message.rodadas());
            messagingTemplate.convertAndSend("/topic/scenes/" + sceneId + "/initiative",
                    new InitiativeOverriddenEvent(message.characterSheetId(), message.value(), message.rodadas()));
            analytics.record(INITIATIVE, sceneId, message.characterSheetId(), null, message);
        } catch (RuntimeException ex) {
            log.warn("Rejected Iniciativa change in scene {} for participant {}: {}",
                    sceneId, message.characterSheetId(), ex.getMessage());
        }
    }

    /**
     * A PdN's Efeito de Ego owed to every PJ as a temporary point (core 0.0.83) — relayed to every client, which grants
     * it to the PJs it controls and saves them through their own status frames. Not persisted here; a domain-less
     * message is dropped.
     */
    /**
     * A Magia landing on someone another client owns (client 0.0.94) — relayed, never persisted; see {@link
     * SpellLandedMessage}.
     */
    @MessageMapping("/scenes/{sceneId}/spells")
    public void spellLanded(@DestinationVariable String sceneId, @Payload SpellLandedMessage message) {
        if (message == null || message.spellKey() == null || message.targetCharacterSheetId() == null) {
            return;
        }
        messagingTemplate.convertAndSend("/topic/scenes/" + sceneId + "/spells", message);
        analytics.record(SPELL_LANDED, sceneId, message.casterCharacterSheetId(),
                List.of(message.targetCharacterSheetId()), message);
    }

    @MessageMapping("/scenes/{sceneId}/ego-grants")
    public void egoGrant(@DestinationVariable String sceneId, @Payload EgoGrantMessage message) {
        if (message == null || message.domain() == null) {
            return;
        }
        messagingTemplate.convertAndSend("/topic/scenes/" + sceneId + "/ego-grants", new EgoGrantedEvent(message.domain()));
    }

    /**
     * A participant's live state for the rest of the table's boards — its effective size (a Titã
     * Enlouquecido grows), its Frenesi, and whether it must attack the nearest creature. Relayed, never
     * persisted: it lives on the owning client's core sheet, and this topic only lets every other
     * board draw it. Membership is asserted for the same reason {@link #hidden} checks it.
     */
    @MessageMapping("/scenes/{sceneId}/state")
    public void combatantState(@DestinationVariable String sceneId, @Payload CombatantStateMessage message) {
        try {
            sceneService.requireParticipant(sceneId, message.characterSheetId());
            messagingTemplate.convertAndSend(
                    "/topic/scenes/" + sceneId + "/state",
                    new CombatantStateChangedEvent(message.characterSheetId(), message.sizeCategory(),
                            message.frenzyRounds(), message.frenzyModes(), message.compelled(),
                            message.riding(), message.ferocious(), message.concentrating(), message.conditions()));
            analytics.record(COMBATANT_STATE, sceneId, message.characterSheetId(), null, message);
        } catch (RuntimeException ex) {
            log.warn("Rejected state change in scene {} for participant {}: {}",
                    sceneId, message.characterSheetId(), ex.getMessage());
        }
    }

    /**
     * The GM passed in-game time or granted a Descanso (see {@link SceneService#passTime}), broadcast
     * so each client applies it to its own sheets. Only the GM's client offers the control — a
     * client-side restriction, as {@link #resizeGrid}'s is. Rejected silently like {@link #move}.
     */
    @MessageMapping("/scenes/{sceneId}/time")
    public void passTime(@DestinationVariable String sceneId, @Payload SceneTimeMessage message) {
        try {
            messagingTemplate.convertAndSend("/topic/scenes/" + sceneId + "/time",
                    sceneService.passTime(sceneId, message));
            analytics.record(TIME, sceneId, null, message.characterSheetIds(), message);
        } catch (RuntimeException ex) {
            log.warn("Rejected time passing in scene {}: {}", sceneId, ex.getMessage());
        }
    }

    /**
     * A hit landed — reported by the client owning the <b>target</b>, where core mitigated it into what was actually
     * deducted (see {@link DamageDealtMessage}). Recorded for the analytics warehouse and relayed so every log can
     * show it; nothing is persisted on the Scene, since the PV themselves arrive on {@link #status}. Membership of the
     * target, and of the attacker when one is named, is asserted, same reason {@link #conditionChanged} checks it; a
     * malformed message is dropped.
     */
    @MessageMapping("/scenes/{sceneId}/damage")
    public void damage(@DestinationVariable String sceneId, @Payload DamageDealtMessage message) {
        if (message == null || message.targetCharacterSheetId() == null) {
            return;
        }
        try {
            sceneService.requireParticipant(sceneId, message.targetCharacterSheetId());
            if (message.attackerCharacterSheetId() != null) {
                sceneService.requireParticipant(sceneId, message.attackerCharacterSheetId());
            }
            messagingTemplate.convertAndSend("/topic/scenes/" + sceneId + "/damage", message);
            analytics.record(DAMAGE, sceneId, message.attackerCharacterSheetId(),
                    List.of(message.targetCharacterSheetId()), message);
        } catch (RuntimeException ex) {
            log.warn("Rejected damage in scene {} for participant {}: {}",
                    sceneId, message.targetCharacterSheetId(), ex.getMessage());
        }
    }

    /**
     * A hit on a sheet another client owns, still unmitigated (see {@link AttackHitMessage}) — relayed so the owner
     * can apply it — its damage and any Efeitos Críticos it carries — to the real sheet. A hit carrying neither is
     * dropped. Nothing is persisted or recorded: the owner's {@link #damage} report is. Both
     * participants are asserted, same as {@link #damage}; a malformed message is dropped.
     */
    @MessageMapping("/scenes/{sceneId}/hits")
    public void hit(@DestinationVariable String sceneId, @Payload AttackHitMessage message) {
        if (message == null || message.targetCharacterSheetId() == null || !message.carriesAnything()) {
            return;
        }
        try {
            sceneService.requireParticipant(sceneId, message.targetCharacterSheetId());
            if (message.attackerCharacterSheetId() != null) {
                sceneService.requireParticipant(sceneId, message.attackerCharacterSheetId());
            }
            messagingTemplate.convertAndSend("/topic/scenes/" + sceneId + "/hits", message);
        } catch (RuntimeException ex) {
            log.warn("Rejected hit in scene {} for participant {}: {}",
                    sceneId, message.targetCharacterSheetId(), ex.getMessage());
        }
    }

    /** The primary target first, then whoever else an area or chained attack caught. */
    private static List<String> actionTargetsOf(SceneActionEvent event) {
        List<String> targets = new ArrayList<>();
        targets.add(event.targetCharacterSheetId());
        if (event.attackDetails() != null && event.attackDetails().additionalTargetCharacterSheetIds() != null) {
            targets.addAll(event.attackDetails().additionalTargetCharacterSheetIds());
        }
        return targets;
    }

    /** Everyone an activation bound, damaged, conditioned or otherwise touched. */
    private static List<String> abilityTargetsOf(AbilityActivatedEvent event) {
        List<String> targets = new ArrayList<>();
        if (event.boundCharacterSheetIds() != null) {
            targets.addAll(event.boundCharacterSheetIds());
        }
        TitleEffectsDto effects = event.effects();
        if (effects != null) {
            if (effects.areaDamage() != null) {
                effects.areaDamage().forEach(hit -> targets.add(hit.targetCharacterSheetId()));
            }
            if (effects.conditions() != null) {
                effects.conditions().forEach(condition -> targets.add(condition.targetCharacterSheetId()));
            }
            if (effects.targetEffects() != null) {
                effects.targetEffects().forEach(effect -> targets.add(effect.targetCharacterSheetId()));
            }
            if (effects.inspiredFrenzy() != null && effects.inspiredFrenzy().recipientCharacterSheetIds() != null) {
                targets.addAll(effects.inspiredFrenzy().recipientCharacterSheetIds());
            }
        }
        return targets;
    }

    private static GridPosition toGridPosition(GridPositionDto dto) {
        return new GridPosition(dto.x(), dto.y());
    }

    private static GridPositionDto toDto(GridPosition position) {
        return new GridPositionDto(position.x(), position.y());
    }
}
