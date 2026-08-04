package org.aventyrs.api.scene;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.aventyrs.api.player.dto.PlayerRequest;
import org.aventyrs.api.scene.dto.GridPositionDto;
import org.aventyrs.api.scene.dto.SceneCreateRequest;
import org.aventyrs.api.scene.dto.SceneParticipantRequest;
import org.aventyrs.api.scene.dto.SceneUpdateRequest;
import org.aventyrs.api.sheet.dto.CharacterSheetCreateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

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
        CharacterSheetCreateRequest request = new CharacterSheetCreateRequest(UUID.randomUUID().toString(), playerId);
        String response = mockMvc.perform(post("/api/character-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    @Test
    void performsFullCrudLifecycle() throws Exception {
        SceneCreateRequest createRequest = new SceneCreateRequest("Ambush at the bridge");

        String createResponse = mockMvc.perform(post("/api/scenes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Ambush at the bridge"))
                .andExpect(jsonPath("$.participants", hasSize(0)))
                .andExpect(jsonPath("$.currentRound").value(0))
                .andExpect(jsonPath("$.currentIndex").value(-1))
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(createResponse).get("id").asText();

        SceneUpdateRequest updateRequest = new SceneUpdateRequest(
                "Ambush at the bridge",
                List.of(
                        new SceneParticipantRequest(characterSheetId1, 15, "party", new GridPositionDto(10, 10)),
                        new SceneParticipantRequest(characterSheetId2, 8, "enemies", new GridPositionDto(11, 10))),
                0,
                0);

        mockMvc.perform(put("/api/scenes/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants", hasSize(2)))
                .andExpect(jsonPath("$.participants[0].characterSheetId").value(characterSheetId1))
                .andExpect(jsonPath("$.participants[0].position.x").value(10))
                .andExpect(jsonPath("$.participants[0].position.y").value(10))
                .andExpect(jsonPath("$.currentIndex").value(0));

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
    void rejectsUnknownCharacterSheetReference() throws Exception {
        String id = createEmptyScene();

        SceneUpdateRequest updateRequest = new SceneUpdateRequest(
                "Scene",
                List.of(new SceneParticipantRequest(UUID.randomUUID().toString(), 10, "party", new GridPositionDto(0, 0))),
                0,
                0);

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
                        new SceneParticipantRequest(characterSheetId1, 15, "party", new GridPositionDto(5, 5)),
                        new SceneParticipantRequest(characterSheetId2, 8, "enemies", new GridPositionDto(5, 5))),
                0,
                0);

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
                List.of(new SceneParticipantRequest(characterSheetId1, 15, "party", new GridPositionDto(0, 0))),
                0,
                5);

        mockMvc.perform(put("/api/scenes/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsOutOfBoundsGridPosition() throws Exception {
        String requestJson = objectMapper.writeValueAsString(new SceneCreateRequest("Scene"));
        String id = objectMapper.readTree(mockMvc.perform(post("/api/scenes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        SceneUpdateRequest updateRequest = new SceneUpdateRequest(
                "Scene",
                List.of(new SceneParticipantRequest(characterSheetId1, 15, "party", new GridPositionDto(100, 0))),
                0,
                0);

        mockMvc.perform(put("/api/scenes/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());
    }

    private String createEmptyScene() throws Exception {
        String response = mockMvc.perform(post("/api/scenes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SceneCreateRequest("Scene"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }
}
