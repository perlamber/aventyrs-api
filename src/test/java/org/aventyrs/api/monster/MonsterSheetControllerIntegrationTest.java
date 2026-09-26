package org.aventyrs.api.monster;

import org.aventyrs.core.skill.SkillType;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.aventyrs.api.item.dto.InventoryItemDto;
import org.aventyrs.api.monster.dto.MonsterAdjustmentsDto;
import org.aventyrs.api.monster.dto.MonsterBlueprintDto;
import org.aventyrs.api.monster.dto.MonsterSheetCreateRequest;
import org.aventyrs.api.monster.dto.MonstrousAbilityDto;
import org.aventyrs.api.monster.dto.MonsterSheetUpdateRequest;
import org.aventyrs.api.player.dto.PlayerRequest;
import org.aventyrs.api.sheet.dto.BleedingDto;
import org.aventyrs.api.sheet.dto.LifeStealDto;
import org.aventyrs.api.sheet.dto.ManaDrainDto;
import org.aventyrs.api.sheet.dto.PendingEgoRecoveryDto;
import org.aventyrs.api.sheet.dto.TemporaryBonusDto;
import org.aventyrs.api.sheet.dto.WitheringDto;
import org.aventyrs.core.character.AttributeDomain;
import org.aventyrs.core.character.SizeCategory;
import org.aventyrs.core.monster.MonsterKind;
import org.aventyrs.core.monster.model.MonsterModel;
import org.aventyrs.core.character.EgoDomain;
import org.aventyrs.core.effect.CriticalEffectType;
import org.aventyrs.core.item.ItemCategory;
import org.aventyrs.core.item.ItemRarity;
import org.aventyrs.core.item.ItemWeightClass;
import org.aventyrs.core.modifier.ModifierType;
import org.aventyrs.core.rest.RestType;
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

    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MonsterSheetService monsterSheetService;

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

    /** Mirrors core's {@code SampleMonster.GOBLIN_SELVAGEM} — legal, Presa, GP 5. */
    static MonsterBlueprintDto goblin(String name) {
        return new MonsterBlueprintDto(
                name, 5, null, SizeCategory.MINUS_ONE,
                Map.of(AttributeDomain.VIGOR, 3, AttributeDomain.STRENGTH, 3, AttributeDomain.DEXTERITY, 4,
                        AttributeDomain.INSTINCT, 3, AttributeDomain.GNOSE, 2),
                Set.of(SkillType.ATAQUE_CORPO_A_CORPO, SkillType.ESQUIVA_E_APARAR, SkillType.FURTIVIDADE, SkillType.ATTENTION),
                Set.of(SkillType.ATAQUE_CORPO_A_CORPO, SkillType.FURTIVIDADE),
                Map.of(SkillType.ESQUIVA_E_APARAR, 1),
                List.of(MonsterModel.ASPECTO_HUMANOIDE),
                List.of(new MonstrousAbilityDto(MonsterModel.ASPECTO_HUMANOIDE, "CORPO_HUMANOIDE", null),
                        new MonstrousAbilityDto(MonsterModel.ASPECTO_HUMANOIDE, "MASCARA_SOCIAL", null)),
                null, null, null, null, null, 0, 0);
    }

    /** Mirrors core's {@code SampleMonster.PANTERA_DE_CIRENEIA} — legal, Predador, GP 30, Cireneia. */
    static MonsterBlueprintDto pantera() {
        MonsterModel cireneia = MonsterModel.ABENCOADO_DE_CIRENEIA;
        return new MonsterBlueprintDto(
                "Pantera de Cireneia", 30, MonsterKind.REGULAR, SizeCategory.PLUS_ONE,
                Map.of(AttributeDomain.VIGOR, 3, AttributeDomain.STRENGTH, 2, AttributeDomain.DEXTERITY, 5,
                        AttributeDomain.INSTINCT, 3, AttributeDomain.GNOSE, 2),
                Set.of(SkillType.ATAQUE_CORPO_A_CORPO, SkillType.ESQUIVA_E_APARAR, SkillType.FURTIVIDADE, SkillType.ATTENTION),
                Set.of(SkillType.ATAQUE_CORPO_A_CORPO, SkillType.ESQUIVA_E_APARAR),
                Map.of(SkillType.ATAQUE_CORPO_A_CORPO, 3, SkillType.ESQUIVA_E_APARAR, 2, SkillType.FURTIVIDADE, 2),
                List.of(cireneia),
                List.of(new MonstrousAbilityDto(cireneia, "ATRIBUTOS_APRIMORADOS", null),
                        new MonstrousAbilityDto(cireneia, "MOVIMENTO_APRIMORADO", null),
                        new MonstrousAbilityDto(cireneia, "CELERIDADE", null),
                        new MonstrousAbilityDto(cireneia, "RELAMPEJANTE", "REACTIONS"),
                        new MonstrousAbilityDto(cireneia, "LIBERDADE_SELVAGEM", null)),
                null,
                Map.of(EgoDomain.INICIATIVA, 3, EgoDomain.SORTE, 3),
                null, null, null, 0, 0);
    }

    private String create(MonsterSheetCreateRequest request) throws Exception {
        String created = mockMvc.perform(post("/api/monster-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asText();
    }

    static InventoryItemDto roupaPesada() {
        return new InventoryItemDto("ROUPA_PESADA", "Roupa Pesada", null, ItemCategory.ARMOR, ItemRarity.COMMON,
                ItemWeightClass.HEAVY, 0, 2, 0, 0, 0, 0, null, null, List.of(), null, null, null, false);
    }

    /** A foe authored with loot keeps it, for a Saquear to take. */
    @Test
    void storesTheInventoryGivenAtCreation() throws Exception {
        String id = create(new MonsterSheetCreateRequest(goblin("Goblin"), gmId, null, List.of(roupaPesada())));
        mockMvc.perform(get("/api/monster-sheets/{id}", id))
                .andExpect(jsonPath("$.inventory", hasSize(1)))
                .andExpect(jsonPath("$.inventory[0].name").value("Roupa Pesada"))
                .andExpect(jsonPath("$.inventory[0].category").value("ARMOR"));
    }

    /** Every number comes from core: the Goblin Selvagem's Presa numbers, worked out in SampleMonster. */
    @Test
    void derivesEveryNumberFromTheBlueprint() throws Exception {
        mockMvc.perform(post("/api/monster-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MonsterSheetCreateRequest(goblin("Goblin"), gmId, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.blueprint.name").value("Goblin"))
                .andExpect(jsonPath("$.blueprint.kind").value("REGULAR"))
                .andExpect(jsonPath("$.blueprint.abilities", hasSize(2)))
                .andExpect(jsonPath("$.category").value("PRESA"))
                .andExpect(jsonPath("$.physicalDefense").value(16))
                .andExpect(jsonPath("$.magicDefense").value(16))
                .andExpect(jsonPath("$.skillDifficulties.ATAQUE_CORPO_A_CORPO.level").value("EASY"))
                .andExpect(jsonPath("$.skillDifficulties.ATAQUE_CORPO_A_CORPO.bonus").value(1))
                .andExpect(jsonPath("$.skillDifficulties.PERSUASAO.level").value("VERY_EASY"))
                .andExpect(jsonPath("$.generalDifficulty.level").value("VERY_EASY"))
                .andExpect(jsonPath("$.maxHitPoints").value(39))
                .andExpect(jsonPath("$.maxDeterminationPoints").value(28))
                .andExpect(jsonPath("$.maxMagicPoints").value(21))
                .andExpect(jsonPath("$.maxActionPoints").value(3))
                .andExpect(jsonPath("$.violations", hasSize(0)))
                .andExpect(jsonPath("$.character.name").value("Goblin"))
                .andExpect(jsonPath("$.character.race.type").value("MONSTRUOSO"))
                .andExpect(jsonPath("$.character.lifeMultiplier").value(5))
                .andExpect(jsonPath("$.character.attributes.DEXTERITY.base").value(4));
    }

    /** A Cireneia Predador — its Habilidades reach the Defesas, PA and Bônus Racial. */
    @Test
    void appliesTheCireneiaHabilidades() throws Exception {
        mockMvc.perform(post("/api/monster-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MonsterSheetCreateRequest(pantera(), gmId, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("PREDADOR"))
                .andExpect(jsonPath("$.physicalDefense").value(32))
                .andExpect(jsonPath("$.maxHitPoints").value(54))
                .andExpect(jsonPath("$.maxActionPoints").value(5))
                .andExpect(jsonPath("$.character.attributes.DEXTERITY.racialBonus").value(3))
                .andExpect(jsonPath("$.character.egos.SORTE.base").value(5));
    }

    @Test
    void rejectsABlueprintThatBreaksTheRulesListingEveryViolation() throws Exception {
        MonsterBlueprintDto overBudget = new MonsterBlueprintDto(
                "Goblin", 5, null, null,
                Map.of(AttributeDomain.STRENGTH, 5, AttributeDomain.VIGOR, 5, AttributeDomain.DEXTERITY, 5),
                null, null, null, null, null, null, null, null, null, null, 0, 0);

        mockMvc.perform(post("/api/monster-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MonsterSheetCreateRequest(overBudget, gmId, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("MONSTER_RULES_VIOLATED"))
                .andExpect(jsonPath("$.details[0]").value("ATTRIBUTE_POINTS_EXCEEDED::12:10"));
    }

    @Test
    void rejectsAnUnknownHabilidade() throws Exception {
        MonsterBlueprintDto unknown = new MonsterBlueprintDto(
                "Goblin", 5, null, null, null, null, null, null,
                List.of(MonsterModel.ALMA_ELEMENTAL),
                List.of(new MonstrousAbilityDto(MonsterModel.ALMA_ELEMENTAL, "CELERIDADE", null)),
                null, null, null, null, null, 0, 0);

        mockMvc.perform(post("/api/monster-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MonsterSheetCreateRequest(unknown, gmId, null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void performsFullCrudLifecycle() throws Exception {
        String createResponse = mockMvc.perform(post("/api/monster-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MonsterSheetCreateRequest(goblin("Goblin"), gmId, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.character.id").exists())
                .andExpect(jsonPath("$.playerId").value(gmId))
                .andExpect(jsonPath("$.undead").value(false))
                .andExpect(jsonPath("$.criticalEffectImmunities", hasSize(0)))
                .andExpect(jsonPath("$.damageTaken").value(0))
                .andExpect(jsonPath("$.manaSpent").value(0))
                .andExpect(jsonPath("$.determinationSpent").value(0))
                .andExpect(jsonPath("$.shieldPoints").value(0))
                .andExpect(jsonPath("$.temporaryEgoPoints.SORTE").value(0))
                .andExpect(jsonPath("$.temporaryBonuses", hasSize(0)))
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

        // Grown to GP 12: a Deviante now, its PV multiplier with it.
        MonsterBlueprintDto grown = new MonsterBlueprintDto(
                "Goblin Chefe", 12, null, SizeCategory.MINUS_ONE,
                goblin("x").attributeBases(), goblin("x").trainedSkills(), goblin("x").gnoseUpgrades(),
                goblin("x").progressionUpgrades(), goblin("x").models(), goblin("x").abilities(),
                null, null,
                new MonsterAdjustmentsDto(null, null, 2, 0, 0, 0, 0, 0),
                true, Set.of(CriticalEffectType.SANGRAMENTO), 0, 0);
        MonsterSheetUpdateRequest updateRequest = new MonsterSheetUpdateRequest(
                grown,
                gmId,
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
                List.of(roupaPesada()),
                "https://images.aventyrs.test/tokens/goblin.png");

        mockMvc.perform(put("/api/monster-sheets/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.character.id").value(characterId))
                .andExpect(jsonPath("$.character.name").value("Goblin Chefe"))
                .andExpect(jsonPath("$.category").value("DEVIANTE"))
                .andExpect(jsonPath("$.character.lifeMultiplier").value(6))
                .andExpect(jsonPath("$.physicalDefense").value(18))
                .andExpect(jsonPath("$.undead").value(true))
                .andExpect(jsonPath("$.criticalEffectImmunities[0]").value("SANGRAMENTO"))
                .andExpect(jsonPath("$.damageTaken").value(10))
                .andExpect(jsonPath("$.manaSpent").value(4))
                .andExpect(jsonPath("$.determinationSpent").value(1))
                .andExpect(jsonPath("$.shieldPoints").value(3))
                .andExpect(jsonPath("$.temporaryEgoPoints.SORTE").value(2))
                .andExpect(jsonPath("$.temporaryBonuses", hasSize(1)))
                .andExpect(jsonPath("$.bleedingEffects", hasSize(1)))
                .andExpect(jsonPath("$.manaDrains", hasSize(1)))
                .andExpect(jsonPath("$.witheringEffects", hasSize(1)))
                .andExpect(jsonPath("$.pendingEgoRecoveries", hasSize(1)))
                .andExpect(jsonPath("$.lifeSteals", hasSize(1)))
                .andExpect(jsonPath("$.inventory", hasSize(1)))
                .andExpect(jsonPath("$.tokenImageUrl").value("https://images.aventyrs.test/tokens/goblin.png"));

        mockMvc.perform(delete("/api/monster-sheets/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/monster-sheets/{id}", id))
                .andExpect(status().isNotFound());
    }

    /** A foe's token portrait is set when it's authored. */
    @Test
    void storesTheTokenImageUrlGivenAtCreation() throws Exception {
        String id = create(new MonsterSheetCreateRequest(goblin("Goblin"), gmId,
                "https://images.aventyrs.test/tokens/zumbi.png", null));
        mockMvc.perform(get("/api/monster-sheets/{id}", id))
                .andExpect(jsonPath("$.tokenImageUrl").value("https://images.aventyrs.test/tokens/zumbi.png"));
    }

    @Test
    void rejectsCreationForUnknownPlayer() throws Exception {
        MonsterSheetCreateRequest request = new MonsterSheetCreateRequest(
                goblin("Goblin"), UUID.randomUUID().toString(), null, null);

        mockMvc.perform(post("/api/monster-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsCreationWithABlankName() throws Exception {
        MonsterSheetCreateRequest request = new MonsterSheetCreateRequest(goblin(""), gmId, null, null);

        mockMvc.perform(post("/api/monster-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /** A Cena's /status frame for a foe lands on its monster sheet — pools, and Ego when sent. */
    @Test
    void persistsAFoesCombatStatus() throws Exception {
        String id = create(new MonsterSheetCreateRequest(pantera(), gmId, null, null));
        assertTrue(monsterSheetService.exists(id));

        monsterSheetService.updateCombatStatus(id, 7, 0, 3, Map.of(EgoDomain.SORTE, 1));

        mockMvc.perform(get("/api/monster-sheets/{id}", id))
                .andExpect(jsonPath("$.damageTaken").value(7))
                .andExpect(jsonPath("$.determinationSpent").value(3))
                .andExpect(jsonPath("$.temporaryEgoPoints.SORTE").value(1));
    }
}
