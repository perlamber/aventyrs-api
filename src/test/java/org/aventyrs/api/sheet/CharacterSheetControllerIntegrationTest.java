package org.aventyrs.api.sheet;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.aventyrs.api.player.dto.PlayerRequest;
import org.aventyrs.api.sheet.dto.CharacterSheetCreateRequest;
import org.aventyrs.api.sheet.dto.CharacterSheetUpdateRequest;
import org.aventyrs.api.sheet.dto.TemporaryBonusDto;
import org.aventyrs.core.character.EgoDomain;
import org.aventyrs.core.modifier.ModifierType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CharacterSheetControllerIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String playerId;

    @BeforeEach
    void createPlayer() throws Exception {
        PlayerRequest playerRequest = new PlayerRequest("Aragorn Player", "aragorn-" + UUID.randomUUID());
        String response = mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(playerRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        playerId = objectMapper.readTree(response).get("id").asText();
    }

    @Test
    void performsFullCrudLifecycle() throws Exception {
        String characterId = UUID.randomUUID().toString();
        CharacterSheetCreateRequest createRequest = new CharacterSheetCreateRequest(characterId, playerId);

        String createResponse = mockMvc.perform(post("/api/character-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.characterId").value(characterId))
                .andExpect(jsonPath("$.playerId").value(playerId))
                .andExpect(jsonPath("$.totalExperience").value(0))
                .andExpect(jsonPath("$.unUsedExperience").value(0))
                .andExpect(jsonPath("$.damageTaken").value(0))
                .andExpect(jsonPath("$.manaSpent").value(0))
                .andExpect(jsonPath("$.determinationSpent").value(0))
                .andExpect(jsonPath("$.shieldPoints").value(0))
                .andExpect(jsonPath("$.famaPositiva").value(0))
                .andExpect(jsonPath("$.famaNegativa").value(0))
                .andExpect(jsonPath("$.temporaryEgoPoints.AUTOCONTROLE").value(0))
                .andExpect(jsonPath("$.temporaryEgoPoints.RECURSOS").value(0))
                .andExpect(jsonPath("$.temporaryEgoPoints.SORTE").value(0))
                .andExpect(jsonPath("$.temporaryEgoPoints.INICIATIVA").value(0))
                .andExpect(jsonPath("$.temporaryBonuses", hasSize(0)))
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(createResponse).get("id").asText();

        mockMvc.perform(get("/api/character-sheets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.characterId").value(characterId));

        mockMvc.perform(get("/api/character-sheets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        CharacterSheetUpdateRequest updateRequest = new CharacterSheetUpdateRequest(
                characterId,
                playerId,
                new BigDecimal("15"),
                new BigDecimal("5"),
                10,
                4,
                1,
                3,
                2,
                1,
                Map.of(EgoDomain.SORTE, 2),
                List.of(new TemporaryBonusDto(ModifierType.SKILL_ROLL_BONUS, 2, 3)));

        mockMvc.perform(put("/api/character-sheets/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExperience").value(15))
                .andExpect(jsonPath("$.unUsedExperience").value(5))
                .andExpect(jsonPath("$.damageTaken").value(10))
                .andExpect(jsonPath("$.manaSpent").value(4))
                .andExpect(jsonPath("$.determinationSpent").value(1))
                .andExpect(jsonPath("$.shieldPoints").value(3))
                .andExpect(jsonPath("$.famaPositiva").value(2))
                .andExpect(jsonPath("$.famaNegativa").value(1))
                .andExpect(jsonPath("$.temporaryEgoPoints.SORTE").value(2))
                .andExpect(jsonPath("$.temporaryEgoPoints.AUTOCONTROLE").value(0))
                .andExpect(jsonPath("$.temporaryBonuses", hasSize(1)))
                .andExpect(jsonPath("$.temporaryBonuses[0].type").value("SKILL_ROLL_BONUS"))
                .andExpect(jsonPath("$.temporaryBonuses[0].value").value(2))
                .andExpect(jsonPath("$.temporaryBonuses[0].remainingRounds").value(3));

        mockMvc.perform(delete("/api/character-sheets/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/character-sheets/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsCreationForUnknownPlayer() throws Exception {
        CharacterSheetCreateRequest request = new CharacterSheetCreateRequest(
                UUID.randomUUID().toString(), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/character-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsCreationWithBlankCharacterId() throws Exception {
        CharacterSheetCreateRequest request = new CharacterSheetCreateRequest("", playerId);

        mockMvc.perform(post("/api/character-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
