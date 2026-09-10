package org.aventyrs.api.scene;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.aventyrs.api.player.dto.PlayerRequest;
import org.aventyrs.api.scene.dto.AddParticipantRequest;
import org.aventyrs.api.scene.dto.GridPositionDto;
import org.aventyrs.api.scene.dto.MoveRequest;
import org.aventyrs.api.scene.dto.SceneActivatedEvent;
import org.aventyrs.api.scene.dto.SceneCreateRequest;
import org.aventyrs.api.scene.dto.SceneParticipantRequest;
import org.aventyrs.api.scene.dto.SceneUpdateRequest;
import org.aventyrs.api.sheet.dto.CharacterDto;
import org.aventyrs.api.sheet.dto.CharacterSheetCreateRequest;
import org.aventyrs.api.sheet.dto.RaceDto;
import org.aventyrs.core.action.ActionProfile;
import org.aventyrs.core.character.Alignment;
import org.aventyrs.core.character.Character.Sexo;
import org.aventyrs.core.item.ItemRarity;
import org.aventyrs.core.scene.Direction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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

    // withReplicaSet(): the /connections endpoint writes several scene documents in one
    // @Transactional unit, and MongoDB multi-document transactions require a replica set.
    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0").withReplicaSet();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SceneRepository sceneRepository;

    @MockitoSpyBean
    private SimpMessagingTemplate messagingTemplate;

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

    /** The MongoDB container is shared across every test in this class with no per-test wipe, so an
     * activated scene left behind would make {@code getAvailableReturnsMostRecentlyCreatedScene}
     * flaky depending on run order. Clear the flag between tests. */
    @AfterEach
    void clearActiveFlags() {
        List<SceneDocument> active = sceneRepository.findByActiveTrue();
        active.forEach(scene -> scene.setActive(false));
        sceneRepository.saveAll(active);
    }

    private String createCharacterSheet(String playerId) throws Exception {
        CharacterSheetCreateRequest request = new CharacterSheetCreateRequest(
                new CharacterDto("Scene Character", new RaceDto("HUMAN", null, null, null, null, null),
                        Sexo.MASCULINO, null, Alignment.NEUTRAL, null, ActionProfile.IMPULSIVO, null, null, null, null,
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
                .andExpect(jsonPath("$.itemStoreMaxRarity").doesNotExist())
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
                "https://images.example.com/scenes/bridge.png",
                ItemRarity.UNCOMMON);

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
                .andExpect(jsonPath("$.imageUrl").value("https://images.example.com/scenes/bridge.png"))
                .andExpect(jsonPath("$.itemStoreMaxRarity").value("UNCOMMON"));

        mockMvc.perform(get("/api/scenes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants", hasSize(2)))
                .andExpect(jsonPath("$.itemStoreMaxRarity").value("UNCOMMON"));

        mockMvc.perform(get("/api/scenes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        mockMvc.perform(delete("/api/scenes/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/scenes/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void startCombatFlipsTheFlagAndRejectsASecondCall() throws Exception {
        String id = createEmptyScene();

        mockMvc.perform(post("/api/scenes/{id}/combat", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.combatScene").value(true))
                .andExpect(jsonPath("$.currentRound").value(0));

        mockMvc.perform(get("/api/scenes/{id}", id))
                .andExpect(jsonPath("$.combatScene").value(true));

        mockMvc.perform(post("/api/scenes/{id}/combat", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("SCENE_ALREADY_IN_COMBAT"));
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
                null,
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
    void aNewSceneIsNotActive() throws Exception {
        mockMvc.perform(post("/api/scenes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SceneCreateRequest("Scene", "URBAN", 20, 15))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void activatingASceneMakesItAvailableAndClearsActiveOnEveryOther() throws Exception {
        String older = createEmptyScene();
        Thread.sleep(10);
        String newer = createEmptyScene();

        // Nothing active yet: /available still falls back to the latest created.
        mockMvc.perform(get("/api/scenes/available"))
                .andExpect(jsonPath("$.id").value(newer));

        // Activate the older one — /available now follows active, not creation order.
        mockMvc.perform(put("/api/scenes/{id}/active", older))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(older))
                .andExpect(jsonPath("$.active").value(true));
        mockMvc.perform(get("/api/scenes/available"))
                .andExpect(jsonPath("$.id").value(older));

        // Activating the other flips the flag across — only one scene is ever active.
        mockMvc.perform(put("/api/scenes/{id}/active", newer))
                .andExpect(jsonPath("$.active").value(true));
        mockMvc.perform(get("/api/scenes/{id}", older))
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get("/api/scenes/available"))
                .andExpect(jsonPath("$.id").value(newer));
    }

    @Test
    void activatingAnUnknownSceneIsNotFound() throws Exception {
        mockMvc.perform(put("/api/scenes/{id}/active", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void settingConnectionsWiresBothSidesAndComesBackResolved() throws Exception {
        String here = createEmptyScene();
        String north = createEmptyScene();

        mockMvc.perform(put("/api/scenes/{id}/connections", here)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(Direction.NORTH, north))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connections.NORTH.id").value(north))
                .andExpect(jsonPath("$.connections.NORTH.name").value("Scene"));

        // The neighbour got the opposite-facing link for free.
        mockMvc.perform(get("/api/scenes/{id}", north))
                .andExpect(jsonPath("$.connections.SOUTH.id").value(here));
    }

    @Test
    void settingConnectionsRejectsAMissingNeighbour() throws Exception {
        String here = createEmptyScene();

        mockMvc.perform(put("/api/scenes/{id}/connections", here)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(Direction.NORTH, UUID.randomUUID().toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void movingThroughAConnectionActivatesTheNeighbour() throws Exception {
        String here = createEmptyScene();
        String north = createEmptyScene();
        mockMvc.perform(put("/api/scenes/{id}/connections", here)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(Direction.NORTH, north))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/scenes/{id}/active", here)).andExpect(status().isOk());

        mockMvc.perform(post("/api/scenes/{id}/move/{direction}", here, "NORTH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(north))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get("/api/scenes/{id}", here))
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get("/api/scenes/available"))
                .andExpect(jsonPath("$.id").value(north));
    }

    /** Travelling announces itself on the origin scene's {@code /navigate} topic — that is what
     * every other client still standing there follows to the neighbour. */
    @Test
    void movingThroughAConnectionAnnouncesTheStepOnTheOriginTopic() throws Exception {
        String here = createEmptyScene();
        String north = createEmptyScene();
        mockMvc.perform(put("/api/scenes/{id}/connections", here)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(Direction.NORTH, north))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/scenes/{id}/active", here)).andExpect(status().isOk());

        mockMvc.perform(post("/api/scenes/{id}/move/{direction}", here, "NORTH"))
                .andExpect(status().isOk());

        ArgumentCaptor<SceneActivatedEvent> published = ArgumentCaptor.forClass(SceneActivatedEvent.class);
        verify(messagingTemplate, timeout(2000).atLeastOnce())
                .convertAndSend(eq("/topic/scenes/" + here + "/navigate"), published.capture());
        assertThat(published.getValue().sceneId()).isEqualTo(north);
        assertThat(published.getValue().name()).isEqualTo("Scene");
    }

    /** Travelling with a carry list moves those groups' participants into the destination and
     * leaves the rest behind. */
    @Test
    void movingCarriesTheChosenGroupsIntoTheNeighbour() throws Exception {
        String here = createEmptyScene();
        String north = createEmptyScene();
        mockMvc.perform(put("/api/scenes/{id}/connections", here)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(Direction.NORTH, north))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/scenes/{id}/active", here)).andExpect(status().isOk());

        UUID party = UUID.randomUUID();
        UUID foes = UUID.randomUUID();
        mockMvc.perform(post("/api/scenes/{id}/participants", here)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddParticipantRequest(characterSheetId1, 15, party))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/scenes/{id}/participants", here)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddParticipantRequest(characterSheetId2, 8, foes))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/scenes/{id}/move/{direction}", here, "NORTH")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MoveRequest(Set.of(party)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(north))
                .andExpect(jsonPath("$.participants", hasSize(1)))
                .andExpect(jsonPath("$.participants[0].characterSheetId").value(characterSheetId1));

        // The party left; the foes stayed.
        mockMvc.perform(get("/api/scenes/{id}", here))
                .andExpect(jsonPath("$.participants", hasSize(1)))
                .andExpect(jsonPath("$.participants[0].characterSheetId").value(characterSheetId2));
    }

    /** Carried travellers arrive lined up against the edge they walked in through — heading NORTH
     * drops them along the southern edge of the next scene — keeping the spacing between them
     * rather than all landing on cell (0,0). */
    @Test
    void movingPlacesTravellersAgainstTheOppositeEdgeKeepingTheirSpacing() throws Exception {
        String here = createEmptyScene();
        String north = createEmptyScene();
        mockMvc.perform(put("/api/scenes/{id}/connections", here)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(Direction.NORTH, north))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/scenes/{id}/active", here)).andExpect(status().isOk());

        UUID party = UUID.randomUUID();
        mockMvc.perform(post("/api/scenes/{id}/participants", here)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddParticipantRequest(characterSheetId1, 15, party))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/scenes/{id}/participants", here)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddParticipantRequest(characterSheetId2, 8, party))))
                .andExpect(status().isCreated());

        // They joined the origin at (0,0) and (1,0) — one column apart along the N/S edge.
        mockMvc.perform(post("/api/scenes/{id}/move/{direction}", here, "NORTH")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MoveRequest(Set.of(party)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(north))
                .andExpect(jsonPath("$.participants", hasSize(2)))
                .andExpect(jsonPath("$.participants[0].position.y").value(99))
                .andExpect(jsonPath("$.participants[1].position.y").value(99))
                .andExpect(jsonPath("$.participants[0].position.x").value(49))
                .andExpect(jsonPath("$.participants[1].position.x").value(50));
    }

    @Test
    void movingWithNoConnectionThatWayIsNotFound() throws Exception {
        String here = createEmptyScene();

        mockMvc.perform(post("/api/scenes/{id}/move/{direction}", here, "WEST"))
                .andExpect(status().isNotFound());
    }

    @Test
    void movingInAnUnknownDirectionIsBadRequest() throws Exception {
        String here = createEmptyScene();

        mockMvc.perform(post("/api/scenes/{id}/move/{direction}", here, "UP"))
                .andExpect(status().isBadRequest());
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
                null,
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
                null,
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
                null,
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
                null,
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
