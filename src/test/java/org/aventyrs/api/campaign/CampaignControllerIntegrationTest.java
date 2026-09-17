package org.aventyrs.api.campaign;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.aventyrs.api.campaign.dto.CampaignCreateRequest;
import org.aventyrs.api.player.PlayerService;
import org.aventyrs.api.player.dto.PlayerRequest;
import org.aventyrs.api.sheet.CharacterSheetService;
import org.aventyrs.api.sheet.dto.CharacterDto;
import org.aventyrs.api.sheet.dto.CharacterSheetCreateRequest;
import org.aventyrs.api.sheet.dto.CharacterSheetResponse;
import org.aventyrs.api.sheet.dto.RaceDto;
import org.aventyrs.core.action.ActionProfile;
import org.aventyrs.core.character.Alignment;
import org.aventyrs.core.character.Character.Sexo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CampaignControllerIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlayerService playerService;

    @Autowired
    private CharacterSheetService characterSheetService;

    @Autowired
    private CampaignService campaignService;

    @Autowired
    private CampaignRepository campaignRepository;

    private String sheetId;
    private String campaignId;

    @BeforeEach
    void createCampaignWithAParticipant() throws Exception {
        String playerId = playerService
                .create(new PlayerRequest("Campaign Player", "campaign-player-" + UUID.randomUUID()))
                .id();
        sheetId = characterSheetService.create(new CharacterSheetCreateRequest(
                new CharacterDto("Campaign Character", new RaceDto("HUMAN", null, null, null, null, null),
                        Sexo.FEMININO, null, Alignment.NEUTRAL, null, ActionProfile.IMPULSIVO, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null),
                playerId)).id();

        String response = mockMvc.perform(post("/api/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CampaignCreateRequest("Crônicas"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessions", empty()))
                .andExpect(jsonPath("$.progressionLocked").value(false))
                .andReturn().getResponse().getContentAsString();
        campaignId = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(put("/api/campaigns/{id}/participants/{sheetId}", campaignId, sheetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantSheetIds", contains(sheetId)));
    }

    @Test
    void aSheetOutsideAnyCampaignIsNeverLocked() throws Exception {
        mockMvc.perform(delete("/api/campaigns/{id}/participants/{sheetId}", campaignId, sheetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantSheetIds", empty()));

        mockMvc.perform(get("/api/character-sheets/{id}", sheetId))
                .andExpect(jsonPath("$.campaignId").value(nullValue()))
                .andExpect(jsonPath("$.progressionLocked").value(false));
    }

    @Test
    void sessionsAreNumberedAndLockProgressionOnlyWhileOngoing() throws Exception {
        mockMvc.perform(post("/api/campaigns/{id}/sessions", campaignId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessions[0].number").value(1))
                .andExpect(jsonPath("$.sessions[0].status").value("CREATED"));
        assertSheetLocked(false);

        mockMvc.perform(post("/api/campaigns/{id}/sessions/1/start", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressionLocked").value(true))
                .andExpect(jsonPath("$.ongoingSessionNumber").value(1))
                .andExpect(jsonPath("$.sessions[0].startedAt").isNotEmpty());
        assertSheetLocked(true);

        mockMvc.perform(post("/api/campaigns/{id}/sessions/1/end", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressionLocked").value(false))
                .andExpect(jsonPath("$.ongoingSessionNumber").value(nullValue()))
                .andExpect(jsonPath("$.sessions[0].endedAt").isNotEmpty());
        assertSheetLocked(false);

        mockMvc.perform(post("/api/campaigns/{id}/sessions", campaignId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessions[1].number").value(2));
    }

    @Test
    void aSecondOpenSessionIsAConflict() throws Exception {
        mockMvc.perform(post("/api/campaigns/{id}/sessions", campaignId)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/campaigns/{id}/sessions/1/start", campaignId)).andExpect(status().isOk());

        mockMvc.perform(post("/api/campaigns/{id}/sessions", campaignId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("SESSION_ALREADY_OPEN"));
    }

    @Test
    void anEndedSessionCannotRestart() throws Exception {
        mockMvc.perform(post("/api/campaigns/{id}/sessions", campaignId)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/campaigns/{id}/sessions/1/start", campaignId)).andExpect(status().isOk());
        mockMvc.perform(post("/api/campaigns/{id}/sessions/1/end", campaignId)).andExpect(status().isOk());

        mockMvc.perform(post("/api/campaigns/{id}/sessions/1/start", campaignId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("INVALID_SESSION_TRANSITION"));
        assertSheetLocked(false);
    }

    @Test
    void anUnknownSessionIsAConflictAndAnUnknownCampaignIsNotFound() throws Exception {
        mockMvc.perform(post("/api/campaigns/{id}/sessions/7/start", campaignId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("SESSION_NOT_FOUND"));

        mockMvc.perform(post("/api/campaigns/{id}/sessions", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void addingASheetToAnotherCampaignMovesIt() throws Exception {
        String other = campaignService.create(new CampaignCreateRequest("Outra")).id();

        mockMvc.perform(put("/api/campaigns/{id}/participants/{sheetId}", other, sheetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantSheetIds", contains(sheetId)));

        mockMvc.perform(get("/api/campaigns/{id}", campaignId))
                .andExpect(jsonPath("$.participantSheetIds", empty()));
        mockMvc.perform(get("/api/character-sheets/{id}", sheetId))
                .andExpect(jsonPath("$.campaignId").value(other));
    }

    @Test
    void listingSheetsReportsTheLockAndASheetInNoCampaignIsNeverLocked() {
        campaignService.createSession(campaignId);
        campaignService.startSession(campaignId, 1);

        List<CharacterSheetResponse> all = characterSheetService.list();
        CharacterSheetResponse listed = all.stream().filter(sheet -> sheet.id().equals(sheetId)).findFirst().orElseThrow();

        assertTrue(listed.progressionLocked());
        assertTrue(all.stream().filter(sheet -> sheet.campaignId() == null).noneMatch(CharacterSheetResponse::progressionLocked));
    }

    @Test
    void aStaleCampaignWriteIsRefused() {
        CampaignDocument first = campaignRepository.findById(campaignId).orElseThrow();
        CampaignDocument stale = campaignRepository.findById(campaignId).orElseThrow();

        first.setName("Renomeada");
        campaignRepository.save(first);

        stale.setName("Perdida");
        assertThrows(OptimisticLockingFailureException.class, () -> campaignRepository.save(stale));
    }

    @Test
    void deletingACampaignReleasesItsParticipants() throws Exception {
        campaignService.createSession(campaignId);
        campaignService.startSession(campaignId, 1);

        mockMvc.perform(delete("/api/campaigns/{id}", campaignId)).andExpect(status().isNoContent());

        CharacterSheetResponse sheet = characterSheetService.get(sheetId);
        assertFalse(sheet.progressionLocked());
        mockMvc.perform(get("/api/character-sheets/{id}", sheetId))
                .andExpect(jsonPath("$.campaignId").value(nullValue()));
    }

    private void assertSheetLocked(boolean locked) throws Exception {
        mockMvc.perform(get("/api/character-sheets/{id}", sheetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignId").value(campaignId))
                .andExpect(jsonPath("$.progressionLocked").value(locked));
    }
}
