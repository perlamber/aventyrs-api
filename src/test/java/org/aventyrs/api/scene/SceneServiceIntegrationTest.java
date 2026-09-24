package org.aventyrs.api.scene;

import org.aventyrs.api.scene.dto.SceneParticipantRequest;
import org.aventyrs.api.scene.dto.ConcealmentDto;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.aventyrs.api.scene.dto.RollRequestMessage;
import org.aventyrs.api.scene.dto.RollRequestedEvent;
import org.aventyrs.api.scene.dto.RollResponseMessage;
import org.aventyrs.api.scene.dto.RollRespondedEvent;
import org.aventyrs.core.skill.DifficultyLevel;
import org.aventyrs.core.skill.SkillType;
import java.util.UUID;

import org.aventyrs.api.common.NotFoundException;
import org.aventyrs.api.player.PlayerService;
import org.aventyrs.api.player.dto.PlayerRequest;
import org.aventyrs.api.scene.dto.AddParticipantRequest;
import org.aventyrs.api.scene.dto.GridPositionDto;
import org.aventyrs.api.scene.dto.GridResizedEvent;
import org.aventyrs.api.scene.dto.AbilityActivatedEvent;
import org.aventyrs.api.scene.dto.SceneCreateRequest;
import org.aventyrs.api.scene.dto.SceneParticipantResponse;
import org.aventyrs.api.scene.dto.SceneResponse;
import org.aventyrs.api.scene.dto.TurnAdvancedEvent;
import org.aventyrs.api.sheet.CharacterSheetService;
import org.aventyrs.api.sheet.dto.CharacterDto;
import org.aventyrs.api.sheet.dto.CharacterSheetCreateRequest;
import org.aventyrs.api.sheet.dto.RaceDto;
import org.aventyrs.core.action.ActionProfile;
import org.aventyrs.core.character.Alignment;
import org.aventyrs.api.sheet.dto.CharacterSheetResponse;
import java.util.Map;
import org.aventyrs.core.character.Character.Sexo;
import org.aventyrs.core.character.CharacterStatus;
import org.aventyrs.core.scene.Direction;
import org.aventyrs.core.scene.grid.GridPosition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SceneServiceIntegrationTest {

    // withReplicaSet(): SceneConnectionService#setConnections writes several scene documents in one
    // @Transactional unit, and MongoDB multi-document transactions require a replica set.
    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0").withReplicaSet();

    @Autowired
    private SceneService sceneService;

    @Autowired
    private SceneActivationService sceneActivationService;

    @Autowired
    private SceneConnectionService sceneConnectionService;

    @Autowired
    private SceneRepository sceneRepository;

    @Autowired
    private PlayerService playerService;

    @Autowired
    private org.aventyrs.api.sheet.CharacterSheetRepository characterSheetRepository;

    @Autowired
    private CharacterSheetService characterSheetService;

    private String characterSheetId1;
    private String characterSheetId2;

    @BeforeEach
    void createCharacterSheets() {
        String playerId = playerService
                .create(new PlayerRequest("Scene Player", "scene-player-" + UUID.randomUUID()))
                .id();
        characterSheetId1 = createCharacterSheet(playerId);
        characterSheetId2 = createCharacterSheet(playerId);
    }

    /** The container is shared across this class with no per-test wipe; a lingering active scene
     * would make the getAvailable tests below order-dependent. */
    @AfterEach
    void clearActiveFlags() {
        List<SceneDocument> active = sceneRepository.findByActiveTrue();
        active.forEach(scene -> scene.setActive(false));
        sceneRepository.saveAll(active);
    }

    private String createCharacterSheet(String playerId) {
        CharacterSheetCreateRequest request = new CharacterSheetCreateRequest(
                new CharacterDto("Scene Character", new RaceDto("HUMAN", null, null, null, null, null),
                        Sexo.MASCULINO, null, Alignment.NEUTRAL, null, ActionProfile.IMPULSIVO, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null),
                playerId);
        return characterSheetService.create(request).id();
    }

    @Test
    void aNewSceneIsNotActive() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN", 100, 100)).id();

        assertFalse(sceneService.get(sceneId).active());
    }

    @Test
    void getAvailablePrefersTheSingleActiveSceneOverTheLatestCreated() {
        String older = sceneService.create(new SceneCreateRequest("Older", "URBAN", 100, 100)).id();
        String newer = sceneService.create(new SceneCreateRequest("Newer", "URBAN", 100, 100)).id();

        assertEquals(newer, sceneService.getAvailable().id(), "nothing active: falls back to latest created");

        sceneActivationService.activate(older);

        assertEquals(older, sceneService.getAvailable().id());
        assertTrue(sceneService.get(older).active());
    }

    @Test
    void activatingASceneClearsActiveOnEveryOther() {
        String a = sceneService.create(new SceneCreateRequest("A", "URBAN", 100, 100)).id();
        String b = sceneService.create(new SceneCreateRequest("B", "URBAN", 100, 100)).id();

        sceneActivationService.activate(a);
        sceneActivationService.activate(b);

        assertFalse(sceneService.get(a).active());
        assertTrue(sceneService.get(b).active());
        assertEquals(List.of(b),
                sceneRepository.findByActiveTrue().stream().map(SceneDocument::getId).toList());
    }

    @Test
    void getAvailableFallsBackToLatestCreatedWhenMoreThanOneSceneIsActive() {
        String older = sceneService.create(new SceneCreateRequest("Older", "URBAN", 100, 100)).id();
        String newer = sceneService.create(new SceneCreateRequest("Newer", "URBAN", 100, 100)).id();
        // The single updateMulti in SceneActivationService normally prevents this; force the state a
        // race between two activations could briefly leave.
        forceActive(older);
        forceActive(newer);

        assertEquals(newer, sceneService.getAvailable().id());
    }

    @Test
    void activatingAnUnknownSceneThrowsNotFound() {
        assertThrows(NotFoundException.class, () -> sceneActivationService.activate(UUID.randomUUID().toString()));
    }

    /**
     * A Cena whose {@code abilityHistory} predates the compulsion fields must still load.
     *
     * <p><b>Regression.</b> {@link SceneAbilityEntry} grew three components after entries were
     * already persisted in a live database. One of them was a primitive {@code int}, and Spring
     * Data cannot instantiate a record whose primitive component has no stored value: it refused
     * with "Parameter enchantmentRounds must not be null" and <em>every</em> scene read returned
     * 400, not merely the one holding the older entry — which took the GM console's scene list
     * down with it.
     *
     * <p>Every other test here creates its documents through the current code, so they all carry
     * the new fields and pass straight through the bug. This one writes the older shape on purpose.
     */
    @Test
    void aSceneWhoseAbilityHistoryPredatesTheCompulsionFieldsStillLoads() {
        String sceneId = newScene("Cena Legada");
        SceneDocument document = sceneRepository.findById(sceneId).orElseThrow();
        document.setAbilityHistory(List.of(new SceneAbilityEntry(
                characterSheetId1, "SANTO", "ORGULHO_ELDURIANO", "Orgulho Elduriano",
                3, 0, 7, List.of(),
                // The four that did not exist when such an entry was first written.
                null, null, null, null)));
        sceneRepository.save(document);

        SceneResponse response = sceneService.get(sceneId);

        assertEquals(1, response.abilityHistory().size());
        AbilityActivatedEvent event = response.abilityHistory().get(0);
        assertEquals("ORGULHO_ELDURIANO", event.abilityId());
        assertEquals(3, event.determinationPointsSpent());
        assertEquals(0, event.enchantmentRounds(), "a missing count reads as none, not a failure");
        assertEquals(List.of(), event.boundCharacterSheetIds());
    }

    private void forceActive(String sceneId) {
        SceneDocument document = sceneRepository.findById(sceneId).orElseThrow();
        document.setActive(true);
        sceneRepository.save(document);
    }

    private String newScene(String name) {
        return sceneService.create(new SceneCreateRequest(name, "URBAN", 100, 100)).id();
    }

    @Test
    void setConnectionsEstablishesBothSidesOfTheLink() {
        String a = newScene("A");
        String b = newScene("B");

        sceneConnectionService.setConnections(a, Map.of(Direction.NORTH, b));

        assertEquals(b, sceneService.get(a).connections().get(Direction.NORTH).id());
        assertEquals("B", sceneService.get(a).connections().get(Direction.NORTH).name());
        // The opposite side is written on the neighbour without the caller touching it.
        assertEquals(a, sceneService.get(b).connections().get(Direction.SOUTH).id());
    }

    @Test
    void rePointingADirectionDetachesTheFormerNeighbourOnBothSides() {
        String a = newScene("A");
        String b = newScene("B");
        String c = newScene("C");
        sceneConnectionService.setConnections(a, Map.of(Direction.NORTH, b));

        sceneConnectionService.setConnections(a, Map.of(Direction.NORTH, c));

        assertEquals(c, sceneService.get(a).connections().get(Direction.NORTH).id());
        assertEquals(a, sceneService.get(c).connections().get(Direction.SOUTH).id());
        assertFalse(sceneService.get(b).connections().containsKey(Direction.SOUTH),
                "B's back-link to A is gone once A points north at C instead");
    }

    @Test
    void clearingAConnectionRemovesTheNeighboursMirroredLink() {
        String a = newScene("A");
        String b = newScene("B");
        sceneConnectionService.setConnections(a, Map.of(Direction.NORTH, b));

        sceneConnectionService.setConnections(a, Map.of());

        assertTrue(sceneService.get(a).connections().isEmpty());
        assertFalse(sceneService.get(b).connections().containsKey(Direction.SOUTH));
    }

    @Test
    void connectingToANeighbourThatAlreadyHasThatSideTakenDetachesTheThirdScene() {
        String a = newScene("A");
        String b = newScene("B");
        String d = newScene("D");
        // D <-> B along B's south side.
        sceneConnectionService.setConnections(d, Map.of(Direction.NORTH, b));

        // Now A claims B's south side.
        sceneConnectionService.setConnections(a, Map.of(Direction.NORTH, b));

        assertEquals(a, sceneService.get(b).connections().get(Direction.SOUTH).id());
        assertFalse(sceneService.get(d).connections().containsKey(Direction.NORTH),
                "D's link to B is severed when A takes B's south side");
    }

    @Test
    void setConnectionsRejectsAMissingNeighbour() {
        String a = newScene("A");

        assertThrows(NotFoundException.class,
                () -> sceneConnectionService.setConnections(a, Map.of(Direction.NORTH, UUID.randomUUID().toString())));
        // The transaction rolled back — nothing was written on A either.
        assertTrue(sceneService.get(a).connections().isEmpty());
    }

    @Test
    void setConnectionsRejectsASceneConnectingToItself() {
        String a = newScene("A");

        assertThrows(IllegalArgumentException.class,
                () -> sceneConnectionService.setConnections(a, Map.of(Direction.NORTH, a)));
    }

    @Test
    void travelActivatesTheNeighbourInThatDirectionAndClearsEveryOther() {
        String here = newScene("Here");
        String north = newScene("North");
        sceneConnectionService.setConnections(here, Map.of(Direction.NORTH, north));
        sceneActivationService.activate(here);

        SceneResponse arrived = sceneConnectionService.travel(here, Direction.NORTH);

        assertEquals(north, arrived.id());
        assertTrue(arrived.active());
        assertFalse(sceneService.get(here).active(), "the scene travelled from goes inactive");
    }

    @Test
    void travelWithNoConnectionThatWayThrowsNotFound() {
        String here = newScene("Here");
        String north = newScene("North");
        sceneConnectionService.setConnections(here, Map.of(Direction.NORTH, north));

        assertThrows(NotFoundException.class, () -> sceneConnectionService.travel(here, Direction.EAST));
    }

    @Test
    void moveParticipantPersistsTheNewPosition() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN", 100, 100)).id();
        UUID group = UUID.randomUUID();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 15, group));

        SceneParticipantEntry moved = sceneService.moveParticipant(sceneId, characterSheetId1, new GridPosition(10, 12));

        assertEquals(new GridPosition(10, 12), moved.position());
        SceneParticipantResponse persisted = sceneService.get(sceneId).participants().get(0);
        assertEquals(10, persisted.position().x());
        assertEquals(12, persisted.position().y());
    }

    @Test
    void aRollRequestIsStampedWithAnIdAndReplayedToLateJoiners() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN", 100, 100)).id();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 15, UUID.randomUUID()));

        RollRequestedEvent event = sceneService.requestRoll(sceneId, new RollRequestMessage(
                RollRequestKind.CHECK, SkillType.ATTENTION, DifficultyLevel.MEDIUM, 0, null,
                List.of(), "Algo se aproxima"));

        assertNotNull(event.requestId(), "the server stamps the id so responses have a stable anchor");
        assertNotNull(event.requestedAt());
        assertEquals(List.of(), event.targetCharacterSheetIds(), "empty targets means the whole table");

        // A client joining now still sees it — the same replay actionHistory already gets.
        SceneResponse replayed = sceneService.get(sceneId);
        assertEquals(1, replayed.rollRequests().size());
        assertEquals(event.requestId(), replayed.rollRequests().get(0).requestId());
        assertEquals("Algo se aproxima", replayed.rollRequests().get(0).prompt());
    }

    @Test
    void anAttackRequestCarriesItsGrauDeDificuldadeAndFlatBonus() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN", 100, 100)).id();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 15, UUID.randomUUID()));

        RollRequestedEvent event = sceneService.requestRoll(sceneId, new RollRequestMessage(
                RollRequestKind.ATTACK, SkillType.ESQUIVA_E_APARAR, DifficultyLevel.HARD, 2,
                "goblin-batedor", List.of(characterSheetId1), null));

        assertEquals(RollRequestKind.ATTACK, event.kind());
        assertEquals(DifficultyLevel.HARD, event.difficultyLevel());
        assertEquals(2, event.attackBonus());
        assertEquals("goblin-batedor", event.attackerCharacterSheetId());
        assertEquals(List.of(characterSheetId1), event.targetCharacterSheetIds());
    }

    @Test
    void aRollRequestNamingAStrangerIsRejected() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN", 100, 100)).id();

        assertThrows(NotFoundException.class, () -> sceneService.requestRoll(sceneId, new RollRequestMessage(
                RollRequestKind.CHECK, SkillType.ATTENTION, DifficultyLevel.EASY, 0, null,
                List.of("not-in-this-scene"), null)));
    }

    /** The verdict is carried, not computed — this server runs no rules engine — and it stays
     * tri-state on the way through: {@code null} means no GD was stated, never a failure. */
    @Test
    void aResponseCarriesTheClientResolvedVerdictIncludingItsNullState() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN", 100, 100)).id();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 15, UUID.randomUUID()));
        String requestId = sceneService.requestRoll(sceneId, new RollRequestMessage(
                RollRequestKind.CHECK, SkillType.ATTENTION, DifficultyLevel.MEDIUM, 0, null,
                List.of(), null)).requestId();

        RollRespondedEvent rolled = sceneService.respondToRoll(sceneId, new RollResponseMessage(
                requestId, characterSheetId1, RollResponseKind.ROLLED, List.of(4, 5, 3),
                Boolean.TRUE, 3, 21, 18, null, null));
        assertEquals(Boolean.TRUE, rolled.succeeded());
        assertEquals(3, rolled.margin());
        assertEquals(List.of(4, 5, 3), rolled.dice());

        RollRespondedEvent reacted = sceneService.respondToRoll(sceneId, new RollResponseMessage(
                requestId, characterSheetId1, RollResponseKind.REACTED, null,
                null, null, null, null, "Conjurando Escudo Arcano", null));
        assertNull(reacted.succeeded(), "a declared reaction states no verdict");
        assertEquals(List.of(), reacted.dice());
        assertEquals("Conjurando Escudo Arcano", reacted.note());

        assertEquals(2, sceneService.get(sceneId).rollResponses().size());
    }

    /**
     * The reason responses are appended with an atomic {@code $push} rather than the
     * read-modify-write save every other mutation here uses.
     *
     * <p>No document in this codebase carries a {@code @Version}, so concurrent full-document saves
     * silently drop one another's writes. Elsewhere that races rarely; here it is the ordinary case
     * — the Narrador asks the whole table to roll and everyone answers at once, on the broker's
     * inbound thread pool. This test fails under {@code repository.save}.
     */
    @Test
    void everyConcurrentResponseSurvives() throws Exception {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN", 100, 100)).id();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 15, UUID.randomUUID()));
        String requestId = sceneService.requestRoll(sceneId, new RollRequestMessage(
                RollRequestKind.CHECK, SkillType.ATTENTION, DifficultyLevel.MEDIUM, 0, null,
                List.of(), null)).requestId();

        int answers = 24;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch startTogether = new CountDownLatch(1);
        try {
            List<Future<?>> submitted = new ArrayList<>();
            for (int i = 0; i < answers; i++) {
                int roll = i;
                submitted.add(pool.submit(() -> {
                    startTogether.await();
                    return sceneService.respondToRoll(sceneId, new RollResponseMessage(
                            requestId, characterSheetId1, RollResponseKind.ROLLED, List.of(1, 2, 3),
                            Boolean.TRUE, roll, roll, 0, null, null));
                }));
            }
            startTogether.countDown();
            for (Future<?> future : submitted) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(answers, sceneService.get(sceneId).rollResponses().size(),
                "every answer must survive: a lost one is a player whose roll silently vanished");
    }

    @Test
    void moveParticipantRejectsAnAlreadyOccupiedCell() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN", 100, 100)).id();
        UUID group = UUID.randomUUID();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 15, group));
        SceneParticipantResponse second =
                sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId2, 8, group));
        GridPosition occupiedCell = new GridPosition(second.position().x(), second.position().y());

        assertThrows(IllegalArgumentException.class,
                () -> sceneService.moveParticipant(sceneId, characterSheetId1, occupiedCell));
    }

    @Test
    void moveParticipantMayShareAnOccupiedCellOnlyWhenFlagged() {
        String sceneId = newScene("Entre as Pernas");
        UUID group = UUID.randomUUID();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 15, group));
        SceneParticipantResponse giant =
                sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId2, 8, group));
        GridPosition giantCell = new GridPosition(giant.position().x(), giant.position().y());

        SceneParticipantEntry moved = sceneService.moveParticipant(sceneId, characterSheetId1, giantCell, true);

        assertEquals(giantCell, moved.position());
        // An already-shared hex does not trip a later, ordinary move of either occupant.
        sceneService.moveParticipant(sceneId, characterSheetId2, giantCell);
        sceneService.moveParticipant(sceneId, characterSheetId2, new GridPosition(40, 40));
        assertThrows(IllegalArgumentException.class,
                () -> sceneService.moveParticipant(sceneId, characterSheetId1, new GridPosition(40, 40)));
    }

    @Test
    void paintedTerrenoDificilPersistsAndClears() {
        String sceneId = newScene("Pântano");

        sceneService.paintTerrain(sceneId, List.of(new GridPosition(1, 1), new GridPosition(2, 2)), true);
        sceneService.paintTerrain(sceneId, List.of(new GridPosition(1, 1)), false);

        assertEquals(List.of(new GridPositionDto(2, 2)), sceneService.get(sceneId).difficultTerrain());
    }

    @Test
    void paintingBeyondTheBoardIsDroppedAndAShrinkPrunesIt() {
        String sceneId = sceneService.create(new SceneCreateRequest("Pequena", "URBAN", 20, 20)).id();

        sceneService.paintTerrain(sceneId, List.of(new GridPosition(5, 5), new GridPosition(15, 15),
                new GridPosition(30, 30)), true);
        assertEquals(2, sceneService.get(sceneId).difficultTerrain().size(), "the off-board cell is dropped");

        sceneService.resizeGrid(sceneId, 10, 10);

        assertEquals(List.of(new GridPositionDto(5, 5)), sceneService.get(sceneId).difficultTerrain());
    }

    /** A document written before difficultTerrain existed has no such field at all. */
    @Test
    void aScenePersistedBeforeTerrenoDificilStillLoads() {
        String sceneId = newScene("Cena Antiga");
        SceneDocument document = sceneRepository.findById(sceneId).orElseThrow();
        document.setDifficultTerrain(null);
        sceneRepository.save(document);

        assertEquals(List.of(), sceneService.get(sceneId).difficultTerrain());
        sceneService.paintTerrain(sceneId, List.of(new GridPosition(3, 3)), true);
        assertEquals(1, sceneService.get(sceneId).difficultTerrain().size());
    }

    @Test
    void moveParticipantRejectsAnUnknownParticipant() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN", 100, 100)).id();

        assertThrows(NotFoundException.class,
                () -> sceneService.moveParticipant(sceneId, characterSheetId1, new GridPosition(0, 0)));
    }

    @Test
    void resizeGridPersistsTheNewExtent() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN", 100, 100)).id();

        assertEquals(new GridResizedEvent(24, 18), sceneService.resizeGrid(sceneId, 24, 18));

        SceneResponse persisted = sceneService.get(sceneId);
        assertEquals(24, persisted.width());
        assertEquals(18, persisted.height());
    }

    @Test
    void resizeGridRejectsAShrinkThatWouldStrandAParticipant() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN", 100, 100)).id();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 15, UUID.randomUUID()));
        sceneService.moveParticipant(sceneId, characterSheetId1, new GridPosition(30, 4));

        assertThrows(IllegalArgumentException.class, () -> sceneService.resizeGrid(sceneId, 20, 20));

        SceneResponse untouched = sceneService.get(sceneId);
        assertEquals(100, untouched.width());
        assertEquals(100, untouched.height());
    }

    @Test
    void resizeGridRejectsAnExtentOutsideTheGridCeiling() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN", 100, 100)).id();

        assertThrows(IllegalArgumentException.class, () -> sceneService.resizeGrid(sceneId, 0, 20));
        assertThrows(IllegalArgumentException.class,
                () -> sceneService.resizeGrid(sceneId, 20, GridPosition.GRID_SIZE + 1));
    }

    @Test
    void requireParticipantAcceptsAParticipantAndRejectsAnOutsider() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN", 100, 100)).id();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 15, UUID.randomUUID()));

        sceneService.requireParticipant(sceneId, characterSheetId1);

        assertThrows(NotFoundException.class,
                () -> sceneService.requireParticipant(sceneId, characterSheetId2));
    }

    /** The status topic's write path: only the two combat-state fields move, so a token going
     * from CLEAN to LOW_LIFE mid-scene can never take the rest of the sheet with it. */
    @Test
    void updateCombatStatusPersistsOnlyTheDamageAndTheStatusTier() {
        CharacterSheetResponse before = characterSheetService.get(characterSheetId1);
        assertEquals(CharacterStatus.CLEAN, before.character().status());
        assertEquals(0, before.damageTaken());

        characterSheetService.updateCombatStatus(characterSheetId1, 13, 0, 0, CharacterStatus.LOW_LIFE);

        CharacterSheetResponse after = characterSheetService.get(characterSheetId1);
        assertEquals(CharacterStatus.LOW_LIFE, after.character().status());
        assertEquals(13, after.damageTaken());

        // Everything else is byte-for-byte what it was: the identity/build fields the scene has
        // no business touching, and the play-time values it didn't send.
        assertEquals(before.character().name(), after.character().name());
        assertEquals(before.character().sexo(), after.character().sexo());
        assertEquals(before.character().actionProfile(), after.character().actionProfile());
        assertEquals(before.character().attributes(), after.character().attributes());
        assertEquals(before.character().actionPoints(), after.character().actionPoints());
        assertEquals(before.character().reactions(), after.character().reactions());
        assertEquals(before.playerId(), after.playerId());
        assertEquals(before.manaSpent(), after.manaSpent());
        assertEquals(before.inventory(), after.inventory());
    }

    @Test
    void updateCombatStatusRejectsAnUnknownCharacterSheet() {
        assertThrows(NotFoundException.class,
                () -> characterSheetService.updateCombatStatus("no-such-sheet", 5, 0, 0, CharacterStatus.FALLEN));
    }

    private String createThirdCharacterSheet() {
        String playerId = playerService
                .create(new PlayerRequest("Scene Player", "scene-player-" + UUID.randomUUID()))
                .id();
        return createCharacterSheet(playerId);
    }

    @Test
    void addParticipantPlacesTheFirstArrivalsInInitiativeOrder() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN", 100, 100)).id();
        UUID group = UUID.randomUUID();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 8, group));
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId2, 15, group));

        SceneResponse scene = sceneService.get(sceneId);
        assertEquals(characterSheetId2, scene.participants().get(0).characterSheetId());
        assertEquals(characterSheetId1, scene.participants().get(1).characterSheetId());
        assertEquals(0, scene.participants().get(0).joinedAtRound());
    }

    @Test
    void aParticipantAddedHiddenCarriesItsConcealmentInTheScene() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN", 100, 100)).id();
        UUID group = UUID.randomUUID();
        ConcealmentDto concealment = new ConcealmentDto(DifficultyLevel.MEDIUM, 3, 21, 19);
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 8, group, concealment));
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId2, 15, group));

        SceneResponse scene = sceneService.get(sceneId);
        assertEquals(concealment, participant(scene, characterSheetId1).concealment());
        assertNull(participant(scene, characterSheetId2).concealment());
    }

    @Test
    void setConcealmentHidesAndRevealsAParticipant() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN", 100, 100)).id();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 8, UUID.randomUUID()));
        ConcealmentDto concealment = new ConcealmentDto(DifficultyLevel.HARD, 1, 24, 21);

        sceneService.setConcealment(sceneId, characterSheetId1, concealment);
        assertEquals(concealment, participant(sceneService.get(sceneId), characterSheetId1).concealment());

        sceneService.setConcealment(sceneId, characterSheetId1, null);
        assertNull(participant(sceneService.get(sceneId), characterSheetId1).concealment());
    }

    /** A concealment survives a move — moving hidden is the client's call to reveal, not the API's. */
    @Test
    void aHiddenParticipantStaysHiddenAcrossAMoveAndABulkUpdate() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN", 100, 100)).id();
        UUID group = UUID.randomUUID();
        ConcealmentDto concealment = new ConcealmentDto(DifficultyLevel.EASY, 2, 16, 15);
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 8, group, concealment));

        sceneService.moveParticipant(sceneId, characterSheetId1, new GridPosition(4, 4));
        assertEquals(concealment, participant(sceneService.get(sceneId), characterSheetId1).concealment());

        sceneService.update(sceneId, new org.aventyrs.api.scene.dto.SceneUpdateRequest("Renamed",
                List.of(new SceneParticipantRequest(characterSheetId1, 8, group, new GridPositionDto(4, 4), 0)),
                0, -1, false, null, null));
        assertEquals(concealment, participant(sceneService.get(sceneId), characterSheetId1).concealment());
    }

    private static SceneParticipantResponse participant(SceneResponse scene, String characterSheetId) {
        return scene.participants().stream()
                .filter(entry -> entry.characterSheetId().equals(characterSheetId))
                .findFirst().orElseThrow();
    }

    @Test
    void advanceTurnThrowsWhenTheSceneHasNoParticipants() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN", 100, 100)).id();

        assertThrows(IllegalArgumentException.class, () -> sceneService.advanceTurn(sceneId));
    }

    @Test
    void advanceTurnWalksInitiativeOrderThenWrapsIntoTheNextRound() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN", 100, 100)).id();
        UUID group = UUID.randomUUID();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 8, group));
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId2, 15, group));
        sceneService.startCombat(sceneId);

        TurnAdvancedEvent first = sceneService.advanceTurn(sceneId);
        assertEquals(characterSheetId2, first.characterSheetId());
        assertEquals(0, first.currentRound());
        assertEquals(0, first.currentIndex());

        TurnAdvancedEvent second = sceneService.advanceTurn(sceneId);
        assertEquals(characterSheetId1, second.characterSheetId());
        assertEquals(0, second.currentRound());
        assertEquals(1, second.currentIndex());

        TurnAdvancedEvent wrapped = sceneService.advanceTurn(sceneId);
        assertEquals(characterSheetId2, wrapped.characterSheetId());
        assertEquals(1, wrapped.currentRound());
        assertEquals(0, wrapped.currentIndex());
    }

    @Test
    void advanceTurnBeforeCombatCyclesTheCursorButLeavesTheRoundAtZero() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN", 100, 100)).id();
        UUID group = UUID.randomUUID();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 8, group));
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId2, 15, group));

        assertEquals(0, sceneService.advanceTurn(sceneId).currentRound());
        assertEquals(0, sceneService.advanceTurn(sceneId).currentRound());

        // The wrap back to the top would be Round 1 in combat; outside combat it just cycles.
        TurnAdvancedEvent wrapped = sceneService.advanceTurn(sceneId);
        assertEquals(0, wrapped.currentRound());
        assertEquals(0, wrapped.currentIndex());
        assertEquals(characterSheetId2, wrapped.characterSheetId());
        assertEquals(0, sceneService.get(sceneId).currentRound());
    }

    @Test
    void startCombatFlipsTheFlagAndRefusesASecondCall() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN", 100, 100)).id();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 8, UUID.randomUUID()));

        assertEquals(false, sceneService.get(sceneId).combatScene());
        SceneResponse started = sceneService.startCombat(sceneId);
        assertEquals(true, started.combatScene());
        assertEquals(true, sceneService.get(sceneId).combatScene());

        IllegalStateException rejected =
                assertThrows(IllegalStateException.class, () -> sceneService.startCombat(sceneId));
        assertEquals("SCENE_ALREADY_IN_COMBAT", rejected.getMessage());
    }

    @Test
    void endCombatFlipsTheFlagOffResetsTheRodadaAndRefusesASceneNotInCombat() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN", 100, 100)).id();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 8, UUID.randomUUID()));

        IllegalStateException early =
                assertThrows(IllegalStateException.class, () -> sceneService.endCombat(sceneId));
        assertEquals("SCENE_NOT_IN_COMBAT", early.getMessage());

        sceneService.startCombat(sceneId);
        sceneService.advanceTurn(sceneId);
        sceneService.advanceTurn(sceneId);
        sceneService.advanceTurn(sceneId);
        assertTrue(sceneService.get(sceneId).currentRound() > 0);

        SceneResponse ended = sceneService.endCombat(sceneId);
        assertEquals(false, ended.combatScene());
        assertEquals(0, ended.currentRound());
        assertEquals(false, sceneService.get(sceneId).combatScene());

        assertEquals(true, sceneService.startCombat(sceneId).combatScene(), "a later combat may start again");
    }

    @Test
    void updateRejectsALaterRoundOnANonCombatScene() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN", 100, 100)).id();

        assertThrows(IllegalArgumentException.class, () -> sceneService.update(sceneId,
                new org.aventyrs.api.scene.dto.SceneUpdateRequest("Scene", List.of(), 3, -1, false, null, null)));
    }

    @Test
    void advanceTurnPersistsTheCursorItMovedTo() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN", 100, 100)).id();
        UUID group = UUID.randomUUID();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 15, group));

        sceneService.advanceTurn(sceneId);

        SceneResponse persisted = sceneService.get(sceneId);
        assertEquals(0, persisted.currentRound());
        assertEquals(0, persisted.currentIndex());
    }

    @Test
    void aParticipantJoiningMidRoundWaitsForTheNextRoundBoundary() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN", 100, 100)).id();
        UUID group = UUID.randomUUID();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 15, group));
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId2, 8, group));
        sceneService.startCombat(sceneId);
        sceneService.advanceTurn(sceneId);

        String latecomerId = createThirdCharacterSheet();
        SceneParticipantResponse latecomer =
                sceneService.addParticipant(sceneId, new AddParticipantRequest(latecomerId, 12, group));

        // Held back: joinedAtRound is the round after the one in progress, and it sits in the
        // list's tail rather than at its sorted position.
        assertEquals(1, latecomer.joinedAtRound());
        assertEquals(latecomerId, sceneService.get(sceneId).participants().get(2).characterSheetId());

        // Second turn of round 0 still belongs to the original pair.
        assertEquals(characterSheetId2, sceneService.advanceTurn(sceneId).characterSheetId());

        // The wrap merges the latecomer in at its sorted position, between 15 and 8.
        TurnAdvancedEvent wrapped = sceneService.advanceTurn(sceneId);
        assertEquals(1, wrapped.currentRound());
        assertEquals(characterSheetId1, wrapped.characterSheetId());
        SceneResponse merged = sceneService.get(sceneId);
        assertEquals(characterSheetId1, merged.participants().get(0).characterSheetId());
        assertEquals(latecomerId, merged.participants().get(1).characterSheetId());
        assertEquals(characterSheetId2, merged.participants().get(2).characterSheetId());
        assertEquals(latecomerId, sceneService.advanceTurn(sceneId).characterSheetId());
    }

    @Test
    void removingSomeoneBeforeTheCursorKeepsTheSameParticipantActive() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN", 100, 100)).id();
        UUID group = UUID.randomUUID();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 15, group));
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId2, 8, group));
        sceneService.advanceTurn(sceneId);
        sceneService.advanceTurn(sceneId);

        sceneService.removeParticipant(sceneId, characterSheetId1);

        SceneResponse scene = sceneService.get(sceneId);
        assertEquals(0, scene.currentIndex());
        assertEquals(characterSheetId2, scene.participants().get(0).characterSheetId());
    }

    @Test
    void removingTheLastParticipantResetsTheCursor() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN", 100, 100)).id();
        UUID group = UUID.randomUUID();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 15, group));
        sceneService.advanceTurn(sceneId);

        sceneService.removeParticipant(sceneId, characterSheetId1);

        assertEquals(-1, sceneService.get(sceneId).currentIndex());
    }

    // --- Gigante Enfurecido: the Ego state a live Cena persists ----------------------------------

    @Test
    void combatStatusPersistsTheEgoStateWhenSentAndLeavesItAloneWhenNot() {
        characterSheetService.updateCombatStatus(characterSheetId1, 0, 0, 0, CharacterStatus.CLEAN,
                Map.of(org.aventyrs.core.character.EgoDomain.AUTOCONTROLE, 3),
                List.of(new org.aventyrs.api.sheet.dto.HourlyEgoRecoveryDto(
                        org.aventyrs.core.character.EgoDomain.AUTOCONTROLE, 3, 2, 1)),
                true);

        characterSheetService.updateCombatStatus(characterSheetId1, 4, 0, 0, CharacterStatus.HIGH_LIFE);

        CharacterSheetResponse after = characterSheetService.get(characterSheetId1);
        assertEquals(3, after.temporaryEgoPoints().get(org.aventyrs.core.character.EgoDomain.AUTOCONTROLE));
        assertEquals(List.of(new org.aventyrs.api.sheet.dto.HourlyEgoRecoveryDto(
                org.aventyrs.core.character.EgoDomain.AUTOCONTROLE, 3, 2, 1)), after.hourlyEgoRecoveries());
        assertTrue(after.exhausted());
        assertEquals(4, after.damageTaken());
    }

    @Test
    void aSheetWrittenBeforeTheEgoStateExistedStillReads() {
        var document = characterSheetRepository.findById(characterSheetId2).orElseThrow();
        document.setHourlyEgoRecoveries(null);
        document.setExhausted(null);
        characterSheetRepository.save(document);

        CharacterSheetResponse response = characterSheetService.get(characterSheetId2);

        assertEquals(List.of(), response.hourlyEgoRecoveries());
        assertFalse(response.exhausted());
    }

    @Test
    void anAbilityActivationCarriesItsEffectsOnOthersThroughTheLogAndTheEcho() {
        String sceneId = newScene("Grito");
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 15, UUID.randomUUID()));
        var effects = new org.aventyrs.api.scene.dto.TitleEffectsDto(
                List.of(new org.aventyrs.api.scene.dto.AreaDamageDto(characterSheetId2, 7, "PRIMORDIAL", null)),
                List.of(new org.aventyrs.api.scene.dto.InflictedConditionDto(characterSheetId2, "ABALADO", 2, true)),
                null);

        AbilityActivatedEvent echoed = sceneService.recordAbility(sceneId,
                new org.aventyrs.api.scene.dto.AbilityActivationMessage(characterSheetId1, "GIGANTE_ENFURECIDO",
                        "GRITOS_DE_GUERRA", "Gritos de Guerra", 3, 0, 2, List.of(), null, List.of(), 0, effects));

        assertEquals(effects, echoed.effects());
        assertEquals(effects, sceneService.get(sceneId).abilityHistory().get(0).effects());
    }

    @Test
    void passingTimeValidatesTheRestAndWhoRests() {
        String sceneId = newScene("Acampamento");
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 15, UUID.randomUUID()));

        var event = sceneService.passTime(sceneId,
                new org.aventyrs.api.scene.dto.SceneTimeMessage(4, "CURTO", List.of(characterSheetId1)));

        assertEquals(4, event.hours());
        assertEquals("CURTO", event.restType());
        assertThrows(IllegalArgumentException.class, () -> sceneService.passTime(sceneId,
                new org.aventyrs.api.scene.dto.SceneTimeMessage(-1, null, null)));
        assertThrows(IllegalArgumentException.class, () -> sceneService.passTime(sceneId,
                new org.aventyrs.api.scene.dto.SceneTimeMessage(1, "SONECA", null)));
        assertThrows(RuntimeException.class, () -> sceneService.passTime(sceneId,
                new org.aventyrs.api.scene.dto.SceneTimeMessage(1, null, List.of(characterSheetId2))));
    }
}
