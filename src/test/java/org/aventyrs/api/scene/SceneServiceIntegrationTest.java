package org.aventyrs.api.scene;

import java.util.UUID;

import org.aventyrs.api.common.NotFoundException;
import org.aventyrs.api.player.PlayerService;
import org.aventyrs.api.player.dto.PlayerRequest;
import org.aventyrs.api.scene.dto.AddParticipantRequest;
import org.aventyrs.api.scene.dto.SceneCreateRequest;
import org.aventyrs.api.scene.dto.SceneParticipantResponse;
import org.aventyrs.api.scene.dto.SceneResponse;
import org.aventyrs.api.scene.dto.TurnAdvancedEvent;
import org.aventyrs.api.sheet.CharacterSheetService;
import org.aventyrs.api.sheet.dto.CharacterDto;
import org.aventyrs.api.sheet.dto.CharacterSheetCreateRequest;
import org.aventyrs.api.sheet.dto.RaceDto;
import org.aventyrs.core.action.ActionProfile;
import org.aventyrs.api.sheet.dto.CharacterSheetResponse;
import org.aventyrs.core.character.Character.Sexo;
import org.aventyrs.core.character.CharacterStatus;
import org.aventyrs.core.scene.grid.GridPosition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @Autowired
    private SceneService sceneService;

    @Autowired
    private PlayerService playerService;

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

    private String createCharacterSheet(String playerId) {
        CharacterSheetCreateRequest request = new CharacterSheetCreateRequest(
                new CharacterDto("Scene Character", new RaceDto("HUMAN", null, null, null, null, null),
                        Sexo.MASCULINO, null, 5, null, ActionProfile.IMPULSIVO, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null),
                playerId);
        return characterSheetService.create(request).id();
    }

    @Test
    void moveParticipantPersistsTheNewPosition() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN")).id();
        UUID group = UUID.randomUUID();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 15, group));

        SceneParticipantEntry moved = sceneService.moveParticipant(sceneId, characterSheetId1, new GridPosition(10, 12));

        assertEquals(new GridPosition(10, 12), moved.position());
        SceneParticipantResponse persisted = sceneService.get(sceneId).participants().get(0);
        assertEquals(10, persisted.position().x());
        assertEquals(12, persisted.position().y());
    }

    @Test
    void moveParticipantRejectsAnAlreadyOccupiedCell() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN")).id();
        UUID group = UUID.randomUUID();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 15, group));
        SceneParticipantResponse second =
                sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId2, 8, group));
        GridPosition occupiedCell = new GridPosition(second.position().x(), second.position().y());

        assertThrows(IllegalArgumentException.class,
                () -> sceneService.moveParticipant(sceneId, characterSheetId1, occupiedCell));
    }

    @Test
    void moveParticipantRejectsAnUnknownParticipant() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN")).id();

        assertThrows(NotFoundException.class,
                () -> sceneService.moveParticipant(sceneId, characterSheetId1, new GridPosition(0, 0)));
    }

    @Test
    void requireParticipantAcceptsAParticipantAndRejectsAnOutsider() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN")).id();
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

        characterSheetService.updateCombatStatus(characterSheetId1, 13, CharacterStatus.LOW_LIFE);

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
                () -> characterSheetService.updateCombatStatus("no-such-sheet", 5, CharacterStatus.FALLEN));
    }

    private String createThirdCharacterSheet() {
        String playerId = playerService
                .create(new PlayerRequest("Scene Player", "scene-player-" + UUID.randomUUID()))
                .id();
        return createCharacterSheet(playerId);
    }

    @Test
    void addParticipantPlacesTheFirstArrivalsInInitiativeOrder() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN")).id();
        UUID group = UUID.randomUUID();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 8, group));
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId2, 15, group));

        SceneResponse scene = sceneService.get(sceneId);
        assertEquals(characterSheetId2, scene.participants().get(0).characterSheetId());
        assertEquals(characterSheetId1, scene.participants().get(1).characterSheetId());
        assertEquals(0, scene.participants().get(0).joinedAtRound());
    }

    @Test
    void advanceTurnThrowsWhenTheSceneHasNoParticipants() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN")).id();

        assertThrows(IllegalArgumentException.class, () -> sceneService.advanceTurn(sceneId));
    }

    @Test
    void advanceTurnWalksInitiativeOrderThenWrapsIntoTheNextRound() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN")).id();
        UUID group = UUID.randomUUID();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 8, group));
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId2, 15, group));

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
    void advanceTurnPersistsTheCursorItMovedTo() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN")).id();
        UUID group = UUID.randomUUID();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 15, group));

        sceneService.advanceTurn(sceneId);

        SceneResponse persisted = sceneService.get(sceneId);
        assertEquals(0, persisted.currentRound());
        assertEquals(0, persisted.currentIndex());
    }

    @Test
    void aParticipantJoiningMidRoundWaitsForTheNextRoundBoundary() {
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN")).id();
        UUID group = UUID.randomUUID();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 15, group));
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId2, 8, group));
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
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN")).id();
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
        String sceneId = sceneService.create(new SceneCreateRequest("Scene", "URBAN")).id();
        UUID group = UUID.randomUUID();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(characterSheetId1, 15, group));
        sceneService.advanceTurn(sceneId);

        sceneService.removeParticipant(sceneId, characterSheetId1);

        assertEquals(-1, sceneService.get(sceneId).currentIndex());
    }
}
