package org.aventyrs.api.scene;

import java.util.List;
import java.util.UUID;

import org.aventyrs.api.player.dto.PlayerRequest;
import org.aventyrs.api.scene.dto.AddParticipantRequest;
import org.aventyrs.api.scene.dto.GridPositionDto;
import org.aventyrs.api.scene.dto.SceneCreateRequest;
import org.aventyrs.api.scene.dto.SceneParticipantRequest;
import org.aventyrs.api.scene.dto.SceneUpdateRequest;
import org.aventyrs.api.sheet.dto.CharacterDto;
import org.aventyrs.api.sheet.dto.CharacterSheetCreateRequest;
import org.aventyrs.api.sheet.dto.RaceDto;
import org.aventyrs.core.action.ActionProfile;
import org.aventyrs.core.character.Character.Sexo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SceneControllerIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String characterSheetId1;
    private String characterSheetId2;

    @BeforeEach
    void createCharacterSheets() throws Exception {
        PlayerRequest playerRequest = new PlayerRequest("Scene Player", "scene-player-" + UUID.randomUUID());
        String playerResponse = mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(playerRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String playerId = objectMapper.readTree(playerResponse).get("id").asText();

        characterSheetId1 = createCharacterSheet(playerId);
        characterSheetId2 = createCharacterSheet(playerId);
    }

    private String createCharacterSheet(String playerId) throws Exception {
        CharacterSheetCreateRequest request = new CharacterSheetCreateRequest(
                new CharacterDto("Scene Character", new RaceDto("HUMAN", null, null, null, null, null),
                        Sexo.MASCULINO, null, 5, null, ActionProfile.IMPULSIVO, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null),
                playerId);
        String response = mockMvc.perform(post("/api/character-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    @Test
    void performsFullCrudLifecycle() throws Exception {
        SceneCreateRequest createRequest = new SceneCreateRequest("Ambush at the bridge", "URBAN", 20, 15);

        String createResponse = mockMvc.perform(post("/api/scenes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Ambush at the bridge"))
                .andExpect(jsonPath("$.terrain").value("URBAN"))
                .andExpect(jsonPath("$.participants", hasSize(0)))
                .andExpect(jsonPath("$.currentRound").value(0))
                .andExpect(jsonPath("$.currentIndex").value(-1))
                .andExpect(jsonPath("$.combatScene").value(false))
                .andExpect(jsonPath("$.imageUrl").doesNotExist())
                .andExpect(jsonPath("$.width").value(20))
                .andExpect(jsonPath("$.height").value(15))
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(createResponse).get("id").asText();

        SceneUpdateRequest updateRequest = new SceneUpdateRequest(
                "Ambush at the bridge",
                List.of(
                        new SceneParticipantRequest(characterSheetId1, 15, UUID.randomUUID(), new GridPositionDto(10, 10), 0),
                        new SceneParticipantRequest(characterSheetId2, 8, UUID.randomUUID(), new GridPositionDto(11, 10), 0)),
                0,
                0,
                true,
                "https://images.example.com/scenes/bridge.png");

        mockMvc.perform(put("/api/scenes/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants", hasSize(2)))
                .andExpect(jsonPath("$.participants[0].characterSheetId").value(characterSheetId1))
                .andExpect(jsonPath("$.participants[0].position.x").value(10))
                .andExpect(jsonPath("$.participants[0].position.y").value(10))
                .andExpect(jsonPath("$.currentIndex").value(0))
                .andExpect(jsonPath("$.combatScene").value(true))
                .andExpect(jsonPath("$.imageUrl").value("https://images.example.com/scenes/bridge.png"));

        mockMvc.perform(get("/api/scenes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants", hasSize(2)));

        mockMvc.perform(get("/api/scenes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        mockMvc.perform(delete("/api/scenes/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/scenes/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void addParticipantJoinsWithFirstFreeGridPosition() throws Exception {
        String id = createEmptyScene();
        UUID party = UUID.randomUUID();

        mockMvc.perform(post("/api/scenes/{id}/participants", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddParticipantRequest(characterSheetId1, 15, party))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.characterSheetId").value(characterSheetId1))
                .andExpect(jsonPath("$.initiativeValue").value(15))
                .andExpect(jsonPath("$.group").value(party.toString()))
                .andExpect(jsonPath("$.position.x").value(0))
                .andExpect(jsonPath("$.position.y").value(0));

        mockMvc.perform(post("/api/scenes/{id}/participants", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddParticipantRequest(characterSheetId2, 8, UUID.randomUUID()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position.x").value(1))
                .andExpect(jsonPath("$.position.y").value(0));

        mockMvc.perform(get("/api/scenes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants", hasSize(2)));
    }

    @Test
    void addParticipantRejectsUnknownCharacterSheetReference() throws Exception {
        String id = createEmptyScene();

        mockMvc.perform(post("/api/scenes/{id}/participants", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddParticipantRequest(UUID.randomUUID().toString(), 10, UUID.randomUUID()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void removeParticipantDeletesThemFromTheScene() throws Exception {
        String id = createEmptyScene();

        mockMvc.perform(post("/api/scenes/{id}/participants", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddParticipantRequest(characterSheetId1, 15, UUID.randomUUID()))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/scenes/{id}/participants", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddParticipantRequest(characterSheetId2, 8, UUID.randomUUID()))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/scenes/{id}/participants/{characterSheetId}", id, characterSheetId1))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/scenes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants", hasSize(1)))
                .andExpect(jsonPath("$.participants[0].characterSheetId").value(characterSheetId2));
    }

    @Test
    void removeParticipantRejectsUnknownCharacterSheetReference() throws Exception {
        String id = createEmptyScene();

        mockMvc.perform(delete("/api/scenes/{id}/participants/{characterSheetId}", id, characterSheetId1))
                .andExpect(status().isNotFound());
    }

    @Test
    void listGroupsPartitionsParticipantsByGroup() throws Exception {
        String id = createEmptyScene();
        UUID party = UUID.randomUUID();

        SceneUpdateRequest updateRequest = new SceneUpdateRequest(
                "Scene",
                List.of(
                        new SceneParticipantRequest(characterSheetId1, 15, party, new GridPositionDto(0, 0), 0),
                        new SceneParticipantRequest(characterSheetId2, 8, party, new GridPositionDto(1, 0), 0)),
                0,
                0,
                false,
                null);

        mockMvc.perform(put("/api/scenes/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/scenes/{id}/groups", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].group").value(party.toString()))
                .andExpect(jsonPath("$[0].participants", hasSize(2)))
                .andExpect(jsonPath("$[0].participants[0].characterSheetId").value(characterSheetId1))
                .andExpect(jsonPath("$[0].participants[1].characterSheetId").value(characterSheetId2));
    }

    @Test
    void getAvailableReturnsMostRecentlyCreatedScene() throws Exception {
        createEmptyScene();
        Thread.sleep(10);
        String latestId = createEmptyScene();

        mockMvc.perform(get("/api/scenes/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(latestId));
    }

    @Test
    void rejectsUnknownCharacterSheetReference() throws Exception {
        String id = createEmptyScene();

        SceneUpdateRequest updateRequest = new SceneUpdateRequest(
                "Scene",
                List.of(new SceneParticipantRequest(UUID.randomUUID().toString(), 10, UUID.randomUUID(), new GridPositionDto(0, 0), 0)),
                0,
                0,
                false,
                null);

        mockMvc.perform(put("/api/scenes/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicateGridPositions() throws Exception {
        String id = createEmptyScene();

        SceneUpdateRequest updateRequest = new SceneUpdateRequest(
                "Scene",
                List.of(
                        new SceneParticipantRequest(characterSheetId1, 15, UUID.randomUUID(), new GridPositionDto(5, 5), 0),
                        new SceneParticipantRequest(characterSheetId2, 8, UUID.randomUUID(), new GridPositionDto(5, 5), 0)),
                0,
                0,
                false,
                null);

        mockMvc.perform(put("/api/scenes/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsOutOfBoundsTurnCursor() throws Exception {
        String id = createEmptyScene();

        SceneUpdateRequest updateRequest = new SceneUpdateRequest(
                "Scene",
                List.of(new SceneParticipantRequest(characterSheetId1, 15, UUID.randomUUID(), new GridPositionDto(0, 0), 0)),
                0,
                5,
                false,
                null);

        mockMvc.perform(put("/api/scenes/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsOutOfBoundsGridPosition() throws Exception {
        String requestJson = objectMapper.writeValueAsString(new SceneCreateRequest("Scene", "URBAN", 100, 100));
        String id = objectMapper.readTree(mockMvc.perform(post("/api/scenes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        SceneUpdateRequest updateRequest = new SceneUpdateRequest(
                "Scene",
                List.of(new SceneParticipantRequest(characterSheetId1, 15, UUID.randomUUID(), new GridPositionDto(100, 0), 0)),
                0,
                0,
                false,
                null);

        mockMvc.perform(put("/api/scenes/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsGridDimensionsOverTheHundredCeiling() throws Exception {
        mockMvc.perform(post("/api/scenes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SceneCreateRequest("Scene", "URBAN", 101, 100))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/scenes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SceneCreateRequest("Scene", "URBAN", 100, 0))))
                .andExpect(status().isBadRequest());
    }

    private String createEmptyScene() throws Exception {
        String response = mockMvc.perform(post("/api/scenes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SceneCreateRequest("Scene", "URBAN", 100, 100))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }
}
