package org.aventyrs.api.monster;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.aventyrs.api.monster.dto.MonsterSheetCreateRequest;
import org.aventyrs.api.monster.dto.MonsterSheetUpdateRequest;
import org.aventyrs.api.player.dto.PlayerRequest;
import org.aventyrs.api.sheet.dto.BleedingDto;
import org.aventyrs.api.sheet.dto.CharacterDto;
import org.aventyrs.api.sheet.dto.LifeStealDto;
import org.aventyrs.api.sheet.dto.ManaDrainDto;
import org.aventyrs.api.sheet.dto.PendingEgoRecoveryDto;
import org.aventyrs.api.sheet.dto.RaceDto;
import org.aventyrs.api.sheet.dto.TemporaryBonusDto;
import org.aventyrs.api.sheet.dto.WitheringDto;
import org.aventyrs.core.action.ActionProfile;
import org.aventyrs.core.character.EgoDomain;
import org.aventyrs.core.effect.CriticalEffectType;
import org.aventyrs.core.modifier.ModifierType;
import org.aventyrs.core.rest.RestType;
import org.aventyrs.core.skill.DifficultyLevel;
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
class MonsterSheetControllerIntegrationTest {

    private static final RaceDto MONSTER_RACE = new RaceDto("MONSTRUOSO", null, null, null, null, null);

    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String gmId;

    @BeforeEach
    void createGm() throws Exception {
        PlayerRequest gmRequest = new PlayerRequest("Narrador GM", "gm-" + UUID.randomUUID());
        String response = mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(gmRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        gmId = objectMapper.readTree(response).get("id").asText();
    }

    private static CharacterDto goblinCharacter() {
        return new CharacterDto(
                "Goblin", MONSTER_RACE, null, null, null, null, ActionProfile.REFLEXOS_RAPIDOS,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void performsFullCrudLifecycle() throws Exception {
        MonsterSheetCreateRequest createRequest = new MonsterSheetCreateRequest(
                goblinCharacter(), gmId, 12, 9, DifficultyLevel.HARD, 2, true, Set.of(CriticalEffectType.SANGRAMENTO),
                null);

        String createResponse = mockMvc.perform(post("/api/monster-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.character.id").exists())
                .andExpect(jsonPath("$.character.name").value("Goblin"))
                .andExpect(jsonPath("$.character.race.type").value("MONSTRUOSO"))
                .andExpect(jsonPath("$.playerId").value(gmId))
                .andExpect(jsonPath("$.physicalDefense").value(12))
                .andExpect(jsonPath("$.magicDefense").value(9))
                .andExpect(jsonPath("$.attackDifficulty").value("HARD"))
                .andExpect(jsonPath("$.attackBonus").value(2))
                .andExpect(jsonPath("$.undead").value(true))
                .andExpect(jsonPath("$.criticalEffectImmunities", hasSize(1)))
                .andExpect(jsonPath("$.criticalEffectImmunities[0]").value("SANGRAMENTO"))
                .andExpect(jsonPath("$.damageTaken").value(0))
                .andExpect(jsonPath("$.manaSpent").value(0))
                .andExpect(jsonPath("$.determinationSpent").value(0))
                .andExpect(jsonPath("$.shieldPoints").value(0))
                .andExpect(jsonPath("$.temporaryEgoPoints.SORTE").value(0))
                .andExpect(jsonPath("$.temporaryBonuses", hasSize(0)))
                .andExpect(jsonPath("$.bleedingEffects", hasSize(0)))
                .andExpect(jsonPath("$.manaDrains", hasSize(0)))
                .andExpect(jsonPath("$.witheringEffects", hasSize(0)))
                .andExpect(jsonPath("$.pendingEgoRecoveries", hasSize(0)))
                .andExpect(jsonPath("$.lifeSteals", hasSize(0)))
                .andExpect(jsonPath("$.inventory", hasSize(0)))
                .andExpect(jsonPath("$.tokenImageUrl").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(createResponse).get("id").asText();
        String characterId = objectMapper.readTree(createResponse).get("character").get("id").asText();

        mockMvc.perform(get("/api/monster-sheets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.character.name").value("Goblin"));

        mockMvc.perform(get("/api/monster-sheets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        mockMvc.perform(get("/api/monster-sheets").param("playerId", gmId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id));

        MonsterSheetUpdateRequest updateRequest = new MonsterSheetUpdateRequest(
                goblinCharacter(),
                gmId,
                14,
                10,
                DifficultyLevel.MEDIUM,
                3,
                false,
                Set.of(),
                10,
                4,
                1,
                3,
                Map.of(EgoDomain.SORTE, 2),
                List.of(new TemporaryBonusDto(ModifierType.SKILL_ROLL_BONUS, 2, 3)),
                List.of(new BleedingDto(2, 3)),
                List.of(new ManaDrainDto(1, null)),
                List.of(new WitheringDto(1, 2)),
                List.of(new PendingEgoRecoveryDto(EgoDomain.SORTE, 1, RestType.LONGO)),
                List.of(new LifeStealDto(2, null)),
                List.of("ROUPA_PESADA"),
                "https://images.aventyrs.test/tokens/goblin.png");

        mockMvc.perform(put("/api/monster-sheets/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.character.id").value(characterId))
                .andExpect(jsonPath("$.playerId").value(gmId))
                .andExpect(jsonPath("$.physicalDefense").value(14))
                .andExpect(jsonPath("$.magicDefense").value(10))
                .andExpect(jsonPath("$.attackDifficulty").value("MEDIUM"))
                .andExpect(jsonPath("$.attackBonus").value(3))
                .andExpect(jsonPath("$.undead").value(false))
                .andExpect(jsonPath("$.criticalEffectImmunities", hasSize(0)))
                .andExpect(jsonPath("$.damageTaken").value(10))
                .andExpect(jsonPath("$.manaSpent").value(4))
                .andExpect(jsonPath("$.determinationSpent").value(1))
                .andExpect(jsonPath("$.shieldPoints").value(3))
                .andExpect(jsonPath("$.temporaryEgoPoints.SORTE").value(2))
                .andExpect(jsonPath("$.temporaryBonuses", hasSize(1)))
                .andExpect(jsonPath("$.temporaryBonuses[0].type").value("SKILL_ROLL_BONUS"))
                .andExpect(jsonPath("$.bleedingEffects", hasSize(1)))
                .andExpect(jsonPath("$.manaDrains", hasSize(1)))
                .andExpect(jsonPath("$.witheringEffects", hasSize(1)))
                .andExpect(jsonPath("$.pendingEgoRecoveries", hasSize(1)))
                .andExpect(jsonPath("$.lifeSteals", hasSize(1)))
                .andExpect(jsonPath("$.inventory", hasSize(1)))
                .andExpect(jsonPath("$.inventory[0]").value("ROUPA_PESADA"))
                .andExpect(jsonPath("$.tokenImageUrl").value("https://images.aventyrs.test/tokens/goblin.png"));

        mockMvc.perform(get("/api/monster-sheets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenImageUrl").value("https://images.aventyrs.test/tokens/goblin.png"));

        mockMvc.perform(delete("/api/monster-sheets/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/monster-sheets/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void defaultsAttackDifficultyUndeadAndImmunitiesWhenOmitted() throws Exception {
        MonsterSheetCreateRequest createRequest = new MonsterSheetCreateRequest(
                goblinCharacter(), gmId, 12, 9, null, 2, null, null, null);

        mockMvc.perform(post("/api/monster-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attackDifficulty").value("MEDIUM"))
                .andExpect(jsonPath("$.undead").value(false))
                .andExpect(jsonPath("$.criticalEffectImmunities", hasSize(0)));
    }

    /** A foe's token portrait is set when it's authored — the bestiary is create-and-delete, so
     * creation is the only chance to give one. */
    @Test
    void storesTheTokenImageUrlGivenAtCreation() throws Exception {
        MonsterSheetCreateRequest createRequest = new MonsterSheetCreateRequest(
                goblinCharacter(), gmId, 12, 9, null, 2, null, null,
                "https://images.aventyrs.test/tokens/zumbi.png");

        String created = mockMvc.perform(post("/api/monster-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenImageUrl").value("https://images.aventyrs.test/tokens/zumbi.png"))
                .andReturn().getResponse().getContentAsString();

        // And it survives the round trip, rather than only being echoed back by the create call.
        String id = objectMapper.readTree(created).get("id").asText();
        mockMvc.perform(get("/api/monster-sheets/{id}", id))
                .andExpect(jsonPath("$.tokenImageUrl").value("https://images.aventyrs.test/tokens/zumbi.png"));
    }

    @Test
    void rejectsCreationForUnknownPlayer() throws Exception {
        MonsterSheetCreateRequest request = new MonsterSheetCreateRequest(
                goblinCharacter(), UUID.randomUUID().toString(), 12, 9, null, 2, null, null, null);

        mockMvc.perform(post("/api/monster-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsCreationWithBlankCharacterName() throws Exception {
        CharacterDto blankNamed = new CharacterDto(
                "", MONSTER_RACE, null, null, null, null, ActionProfile.REFLEXOS_RAPIDOS,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        MonsterSheetCreateRequest request = new MonsterSheetCreateRequest(blankNamed, gmId, 12, 9, null, 2, null, null, null);

        mockMvc.perform(post("/api/monster-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
