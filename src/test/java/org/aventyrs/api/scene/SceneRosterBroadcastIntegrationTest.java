package org.aventyrs.api.scene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;

import org.aventyrs.api.player.dto.PlayerRequest;
import org.aventyrs.api.scene.dto.AddParticipantRequest;
import org.aventyrs.api.scene.dto.GridPositionDto;
import org.aventyrs.api.scene.dto.SceneCreateRequest;
import org.aventyrs.api.scene.dto.SceneParticipantRequest;
import org.aventyrs.api.scene.dto.SceneResponse;
import org.aventyrs.api.scene.dto.SceneUpdateRequest;
import org.aventyrs.api.sheet.dto.CharacterDto;
import org.aventyrs.api.sheet.dto.CharacterSheetCreateRequest;
import org.aventyrs.api.sheet.dto.RaceDto;
import org.aventyrs.core.action.ActionProfile;
import org.aventyrs.core.character.Character.Sexo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import tools.jackson.databind.ObjectMapper;

/**
 * Joining a scene is the one live-scene mutation that arrives over REST rather than STOMP (see
 * {@link SceneRealtimeController} for the ones that don't), so until {@link SceneController} began
 * announcing the roster, the other clients standing in that scene were never told: a newcomer
 * showed up on nobody's board or initiative panel until each client happened to re-enter the
 * scene.
 *
 * <p>Asserted at the {@link SimpMessagingTemplate} rather than through a subscribed WebSocket
 * client, because that is the seam this adds. Whether the broker then delivers to subscribers of
 * {@code /topic/scenes/{id}/...} is settled already — every topic in {@link
 * SceneRealtimeController} rides it, and {@code WebSocketConfigIntegrationTest} covers the
 * endpoint itself.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SceneRosterBroadcastIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoSpyBean
    private SimpMessagingTemplate messagingTemplate;

    private String characterSheetId;

    @BeforeEach
    void createCharacterSheet() throws Exception {
        PlayerRequest playerRequest = new PlayerRequest("Roster Player", "roster-player-" + UUID.randomUUID());
        String playerResponse = mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(playerRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        CharacterSheetCreateRequest sheetRequest = new CharacterSheetCreateRequest(
                new CharacterDto("Roster Character", new RaceDto("HUMAN", null, null, null, null, null),
                        Sexo.MASCULINO, null, 5, null, ActionProfile.IMPULSIVO, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null),
                objectMapper.readTree(playerResponse).get("id").asText());
        String sheetResponse = mockMvc.perform(post("/api/character-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sheetRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        characterSheetId = objectMapper.readTree(sheetResponse).get("id").asText();
    }

    @Test
    void announcesTheRosterWhenSomebodyJoins() throws Exception {
        String sceneId = createScene();

        mockMvc.perform(post("/api/scenes/{id}/participants", sceneId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddParticipantRequest(characterSheetId, 15, UUID.randomUUID()))))
                .andExpect(status().isCreated());

        SceneResponse broadcast = rosterBroadcastFor(sceneId);
        assertThat(broadcast.id()).isEqualTo(sceneId);
        assertThat(broadcast.participants()).hasSize(1);
        assertThat(broadcast.participants().get(0).characterSheetId()).isEqualTo(characterSheetId);
        assertThat(broadcast.participants().get(0).initiativeValue()).isEqualTo(15);
        // The cursor rides along with the roster: it is what decides whether the newcomer is in
        // the rotation already or waiting out the Round (see SceneController#broadcastRoster).
        assertThat(broadcast.currentRound()).isZero();
        assertThat(broadcast.currentIndex()).isEqualTo(-1);
    }

    @Test
    void announcesTheRosterWhenSomebodyLeaves() throws Exception {
        String sceneId = createScene();
        mockMvc.perform(post("/api/scenes/{id}/participants", sceneId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddParticipantRequest(characterSheetId, 15, UUID.randomUUID()))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/scenes/{id}/participants/{characterSheetId}", sceneId, characterSheetId))
                .andExpect(status().isNoContent());

        // The captured value is the latest, which is the removal's — the join above sent one too.
        SceneResponse broadcast = rosterBroadcastFor(sceneId);
        assertThat(broadcast.participants()).isEmpty();
    }

    /** A GM's edit through {@code PUT /scenes/{id}} replaces the participant list wholesale, so it
     * is a roster change too — and one every client in the scene has to hear about for the same
     * reason a join is. */
    @Test
    void announcesTheRosterWhenTheSceneIsUpdated() throws Exception {
        String sceneId = createScene();

        mockMvc.perform(put("/api/scenes/{id}", sceneId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SceneUpdateRequest(
                                "Roster scene",
                                List.of(new SceneParticipantRequest(characterSheetId, 11, UUID.randomUUID(),
                                        new GridPositionDto(3, 4), 0)),
                                0, 0, true, null))))
                .andExpect(status().isOk());

        SceneResponse broadcast = rosterBroadcastFor(sceneId);
        assertThat(broadcast.participants()).hasSize(1);
        assertThat(broadcast.participants().get(0).characterSheetId()).isEqualTo(characterSheetId);
        assertThat(broadcast.currentIndex()).isZero();
    }

    /** The most recent scene published to {@code sceneId}'s roster topic. */
    private SceneResponse rosterBroadcastFor(String sceneId) {
        ArgumentCaptor<SceneResponse> published = ArgumentCaptor.forClass(SceneResponse.class);
        verify(messagingTemplate, timeout(2000).atLeastOnce())
                .convertAndSend(eq("/topic/scenes/" + sceneId + "/participants"), published.capture());
        return published.getValue();
    }

    private String createScene() throws Exception {
        String response = mockMvc.perform(post("/api/scenes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SceneCreateRequest("Roster scene", "URBAN", 20, 15))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }
}
