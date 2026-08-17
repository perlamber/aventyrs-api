package org.aventyrs.api.scene;

import java.util.UUID;

import org.aventyrs.api.common.NotFoundException;
import org.aventyrs.api.player.PlayerService;
import org.aventyrs.api.player.dto.PlayerRequest;
import org.aventyrs.api.scene.dto.AddParticipantRequest;
import org.aventyrs.api.scene.dto.SceneCreateRequest;
import org.aventyrs.api.scene.dto.SceneParticipantResponse;
import org.aventyrs.api.sheet.CharacterSheetService;
import org.aventyrs.api.sheet.dto.CharacterDto;
import org.aventyrs.api.sheet.dto.CharacterSheetCreateRequest;
import org.aventyrs.api.sheet.dto.RaceDto;
import org.aventyrs.core.action.ActionProfile;
import org.aventyrs.core.character.Character.Sexo;
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
                        null, null, null, null, null, null, null, null),
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
}
