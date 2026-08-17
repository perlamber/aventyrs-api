package org.aventyrs.api.sheet;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.aventyrs.api.player.dto.PlayerRequest;
import org.aventyrs.api.sheet.dto.AttributeValueDto;
import org.aventyrs.api.sheet.dto.BleedingDto;
import org.aventyrs.api.sheet.dto.CharacterDto;
import org.aventyrs.api.sheet.dto.CharacterSheetCreateRequest;
import org.aventyrs.api.sheet.dto.CharacterSheetUpdateRequest;
import org.aventyrs.api.sheet.dto.CharacterSkillDto;
import org.aventyrs.api.sheet.dto.EgoValueDto;
import org.aventyrs.api.sheet.dto.ManaDrainDto;
import org.aventyrs.api.sheet.dto.PendingEgoRecoveryDto;
import org.aventyrs.api.sheet.dto.RaceDto;
import org.aventyrs.api.sheet.dto.TemporaryBonusDto;
import org.aventyrs.api.sheet.dto.WitheringDto;
import org.aventyrs.core.action.ActionProfile;
import org.aventyrs.core.character.AttributeDomain;
import org.aventyrs.core.character.Character.Sexo;
import org.aventyrs.core.character.Deity;
import org.aventyrs.core.character.EgoDomain;
import org.aventyrs.core.character.SizeCategory;
import org.aventyrs.core.modifier.ModifierType;
import org.aventyrs.core.rest.RestType;
import org.aventyrs.core.skill.SkillType;
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

    private static final RaceDto HUMAN_RACE = new RaceDto("HUMAN", null, null, null, null, null);

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
        CharacterDto character = new CharacterDto(
                "Aragorn Character", HUMAN_RACE, Sexo.MASCULINO, Deity.EPONA, 5, null, ActionProfile.IMPULSIVO, null, null, null, null);
        CharacterSheetCreateRequest createRequest = new CharacterSheetCreateRequest(character, playerId);

        String createResponse = mockMvc.perform(post("/api/character-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.character.id").exists())
                .andExpect(jsonPath("$.character.name").value("Aragorn Character"))
                .andExpect(jsonPath("$.character.race.type").value("HUMAN"))
                .andExpect(jsonPath("$.character.sexo").value("MASCULINO"))
                .andExpect(jsonPath("$.character.deity").value("EPONA"))
                .andExpect(jsonPath("$.character.tendencia").value(5))
                .andExpect(jsonPath("$.character.actionProfile").value("IMPULSIVO"))
                .andExpect(jsonPath("$.character.egos.AUTOCONTROLE.base").value(2))
                .andExpect(jsonPath("$.character.egos.AUTOCONTROLE.total").value(2))
                .andExpect(jsonPath("$.character.egos.RECURSOS.base").value(2))
                .andExpect(jsonPath("$.character.egos.SORTE.base").value(2))
                .andExpect(jsonPath("$.character.egos.INICIATIVA.base").value(2))
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
                .andExpect(jsonPath("$.bleedingEffects", hasSize(0)))
                .andExpect(jsonPath("$.manaDrains", hasSize(0)))
                .andExpect(jsonPath("$.witheringEffects", hasSize(0)))
                .andExpect(jsonPath("$.pendingEgoRecoveries", hasSize(0)))
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(createResponse).get("id").asText();
        String characterId = objectMapper.readTree(createResponse).get("character").get("id").asText();

        mockMvc.perform(get("/api/character-sheets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.character.name").value("Aragorn Character"));

        mockMvc.perform(get("/api/character-sheets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        CharacterDto updatedCharacter = new CharacterDto(
                "Strider", HUMAN_RACE, Sexo.MASCULINO, Deity.TYKHE, 7, null, ActionProfile.CALCULISTA, null, null, null, null);
        CharacterSheetUpdateRequest updateRequest = new CharacterSheetUpdateRequest(
                updatedCharacter,
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
                List.of(new TemporaryBonusDto(ModifierType.SKILL_ROLL_BONUS, 2, 3)),
                List.of(new BleedingDto(2, 3)),
                List.of(new ManaDrainDto(1, null)),
                List.of(new WitheringDto(1, 2)),
                List.of(new PendingEgoRecoveryDto(EgoDomain.SORTE, 1, RestType.LONGO)));

        mockMvc.perform(put("/api/character-sheets/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.character.id").value(characterId))
                .andExpect(jsonPath("$.character.name").value("Strider"))
                .andExpect(jsonPath("$.character.deity").value("TYKHE"))
                .andExpect(jsonPath("$.character.tendencia").value(7))
                .andExpect(jsonPath("$.character.actionProfile").value("CALCULISTA"))
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
                .andExpect(jsonPath("$.temporaryBonuses[0].remainingRounds").value(3))
                .andExpect(jsonPath("$.bleedingEffects", hasSize(1)))
                .andExpect(jsonPath("$.bleedingEffects[0].valuePerRound").value(2))
                .andExpect(jsonPath("$.bleedingEffects[0].remainingRounds").value(3))
                .andExpect(jsonPath("$.manaDrains", hasSize(1)))
                .andExpect(jsonPath("$.manaDrains[0].valuePerRound").value(1))
                .andExpect(jsonPath("$.manaDrains[0].remainingRounds").doesNotExist())
                .andExpect(jsonPath("$.witheringEffects", hasSize(1)))
                .andExpect(jsonPath("$.witheringEffects[0].valuePerRound").value(1))
                .andExpect(jsonPath("$.witheringEffects[0].remainingRounds").value(2))
                .andExpect(jsonPath("$.pendingEgoRecoveries", hasSize(1)))
                .andExpect(jsonPath("$.pendingEgoRecoveries[0].domain").value("SORTE"))
                .andExpect(jsonPath("$.pendingEgoRecoveries[0].value").value(1))
                .andExpect(jsonPath("$.pendingEgoRecoveries[0].minimumRestType").value("LONGO"));

        mockMvc.perform(delete("/api/character-sheets/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/character-sheets/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void persistsSizeCategoryAttributesEgosAndSkills() throws Exception {
        CharacterDto character = new CharacterDto(
                "Boromir Character",
                HUMAN_RACE,
                Sexo.MASCULINO,
                Deity.EPONA,
                5,
                SizeCategory.PLUS_ONE,
                ActionProfile.ESTRATEGISTA,
                Map.of(AttributeDomain.VIGOR, new AttributeValueDto(3, 1, 0)),
                Map.of(EgoDomain.SORTE, new EgoValueDto(3, 2)),
                Map.of(SkillType.ARTES, new CharacterSkillDto(List.of("PINTURA"), List.of("DOM_BARDICO"), 2)),
                List.of("SOBRE_HUMANO", "PASSOS_LONGOS"));
        CharacterSheetCreateRequest createRequest = new CharacterSheetCreateRequest(character, playerId);

        String createResponse = mockMvc.perform(post("/api/character-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.character.sizeCategory").value("PLUS_ONE"))
                .andExpect(jsonPath("$.character.attributes.VIGOR.base").value(3))
                .andExpect(jsonPath("$.character.attributes.VIGOR.racialBonus").value(1))
                .andExpect(jsonPath("$.character.attributes.VIGOR.variable").value(0))
                .andExpect(jsonPath("$.character.attributes.VIGOR.total").value(4))
                .andExpect(jsonPath("$.character.attributes.STRENGTH.base").value(1))
                .andExpect(jsonPath("$.character.attributes.STRENGTH.total").value(1))
                .andExpect(jsonPath("$.character.attributes.CHARISMA.base").value(1))
                .andExpect(jsonPath("$.character.egos.SORTE.base").value(3))
                .andExpect(jsonPath("$.character.egos.SORTE.variable").value(2))
                .andExpect(jsonPath("$.character.egos.SORTE.total").value(5))
                .andExpect(jsonPath("$.character.egos.AUTOCONTROLE.base").value(2))
                .andExpect(jsonPath("$.character.egos.AUTOCONTROLE.total").value(2))
                .andExpect(jsonPath("$.character.skills.ARTES.specializations[0]").value("PINTURA"))
                .andExpect(jsonPath("$.character.skills.ARTES.competencyAbilities[0]").value("DOM_BARDICO"))
                .andExpect(jsonPath("$.character.skills.ARTES.graduationValue").value(2))
                .andExpect(jsonPath("$.character.skills.ATLETISMO").doesNotExist())
                .andExpect(jsonPath("$.character.attributeAbilities[0]").value("SOBRE_HUMANO"))
                .andExpect(jsonPath("$.character.attributeAbilities[1]").value("PASSOS_LONGOS"))
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(createResponse).get("id").asText();

        mockMvc.perform(get("/api/character-sheets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.character.sizeCategory").value("PLUS_ONE"))
                .andExpect(jsonPath("$.character.attributes.VIGOR.total").value(4))
                .andExpect(jsonPath("$.character.egos.SORTE.total").value(5))
                .andExpect(jsonPath("$.character.skills.ARTES.graduationValue").value(2))
                .andExpect(jsonPath("$.character.attributeAbilities[0]").value("SOBRE_HUMANO"));
    }

    /**
     * The real client flow: the creation wizard always POSTs a character with empty skills (no
     * Perícias step there yet), then the hub screen's first "Salvar" PUTs a populated skills map,
     * and a later "Salvar" PUTs a different one again. {@link #performsFullCrudLifecycle} exercises
     * PUT with {@code null} skills, and {@link #persistsSizeCategoryAttributesEgosAndSkills} only
     * exercises POST-with-skills→GET — neither chains a populated-skills PUT after a real create,
     * nor a second PUT that changes the skills map again, which is exactly the sequence a bug that
     * only shows up after more than one write would hide in.
     */
    @Test
    void skillsPopulatedByUpdateSurviveAndCanBeChangedAgain() throws Exception {
        CharacterDto character = new CharacterDto(
                "Boromir Character", HUMAN_RACE, Sexo.MASCULINO, null, 5, null, ActionProfile.ESTRATEGISTA, null, null, Map.of(), null);
        CharacterSheetCreateRequest createRequest = new CharacterSheetCreateRequest(character, playerId);

        String createResponse = mockMvc.perform(post("/api/character-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.character.skills").isEmpty())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(createResponse).get("id").asText();

        CharacterDto firstUpdatedCharacter = new CharacterDto(
                "Boromir Character", HUMAN_RACE, Sexo.MASCULINO, null, 5, null, ActionProfile.ESTRATEGISTA, null, null,
                Map.of(SkillType.ATTENTION, new CharacterSkillDto(null, null, 3),
                        SkillType.FURTIVIDADE, new CharacterSkillDto(List.of("MAESTRIA_DA_OCULTACAO"), List.of("ESCONDER_OUTROS"), 5)),
                List.of("SOBRE_HUMANO", "PASSOS_LONGOS"));
        CharacterSheetUpdateRequest firstUpdateRequest = new CharacterSheetUpdateRequest(
                firstUpdatedCharacter, playerId, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0, 0, 0, 0, Map.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        mockMvc.perform(put("/api/character-sheets/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstUpdateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.character.skills.ATTENTION.graduationValue").value(3))
                .andExpect(jsonPath("$.character.skills.FURTIVIDADE.graduationValue").value(5))
                .andExpect(jsonPath("$.character.skills.FURTIVIDADE.specializations[0]").value("MAESTRIA_DA_OCULTACAO"))
                .andExpect(jsonPath("$.character.skills.FURTIVIDADE.competencyAbilities[0]").value("ESCONDER_OUTROS"));

        mockMvc.perform(get("/api/character-sheets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.character.skills.ATTENTION.graduationValue").value(3))
                .andExpect(jsonPath("$.character.skills.FURTIVIDADE.graduationValue").value(5));

        CharacterDto secondUpdatedCharacter = new CharacterDto(
                "Boromir Character", HUMAN_RACE, Sexo.MASCULINO, null, 5, null, ActionProfile.ESTRATEGISTA, null, null,
                Map.of(SkillType.ATTENTION, new CharacterSkillDto(null, null, 4),
                        SkillType.DOMINIO_DO_MANA, new CharacterSkillDto(null, null, 6)),
                null);
        CharacterSheetUpdateRequest secondUpdateRequest = new CharacterSheetUpdateRequest(
                secondUpdatedCharacter, playerId, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0, 0, 0, 0, Map.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        mockMvc.perform(put("/api/character-sheets/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondUpdateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.character.skills.ATTENTION.graduationValue").value(4))
                .andExpect(jsonPath("$.character.skills.DOMINIO_DO_MANA.graduationValue").value(6))
                .andExpect(jsonPath("$.character.skills.FURTIVIDADE").doesNotExist());

        mockMvc.perform(get("/api/character-sheets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.character.skills.ATTENTION.graduationValue").value(4))
                .andExpect(jsonPath("$.character.skills.DOMINIO_DO_MANA.graduationValue").value(6))
                .andExpect(jsonPath("$.character.skills.FURTIVIDADE").doesNotExist())
                // An update carries the whole build state, so omitting attributeAbilities clears
                // them, exactly like the Perícia dropped above.
                .andExpect(jsonPath("$.character.attributeAbilities").isEmpty());
    }

    @Test
    void defaultsTendenciaWhenOmitted() throws Exception {
        CharacterDto character = new CharacterDto(
                "Gimli", HUMAN_RACE, null, null, null, null, ActionProfile.CONSCIENCIA_DEFENSIVA, null, null, null, null);
        CharacterSheetCreateRequest createRequest = new CharacterSheetCreateRequest(character, playerId);

        mockMvc.perform(post("/api/character-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.character.sexo").doesNotExist())
                .andExpect(jsonPath("$.character.deity").doesNotExist())
                .andExpect(jsonPath("$.character.tendencia").value(1))
                .andExpect(jsonPath("$.character.sizeCategory").value("ZERO"))
                .andExpect(jsonPath("$.character.attributes.VIGOR.base").value(1))
                .andExpect(jsonPath("$.character.attributes.VIGOR.total").value(1))
                .andExpect(jsonPath("$.character.skills").isEmpty());
    }

    @Test
    void filtersByPlayerId() throws Exception {
        PlayerRequest otherPlayerRequest = new PlayerRequest("Legolas Player", "legolas-" + UUID.randomUUID());
        String otherPlayerResponse = mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otherPlayerRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String otherPlayerId = objectMapper.readTree(otherPlayerResponse).get("id").asText();

        CharacterSheetCreateRequest ownSheetRequest = new CharacterSheetCreateRequest(
                new CharacterDto("Legolas Character", HUMAN_RACE, Sexo.MASCULINO, null, 3, null, ActionProfile.IMPULSIVO, null, null, null, null),
                playerId);
        String ownSheetResponse = mockMvc.perform(post("/api/character-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ownSheetRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String ownSheetId = objectMapper.readTree(ownSheetResponse).get("id").asText();

        CharacterSheetCreateRequest otherSheetRequest = new CharacterSheetCreateRequest(
                new CharacterDto("Gimli Character", HUMAN_RACE, Sexo.MASCULINO, null, 3, null, ActionProfile.IMPULSIVO, null, null, null, null),
                otherPlayerId);
        mockMvc.perform(post("/api/character-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otherSheetRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/character-sheets").param("playerId", playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(ownSheetId))
                .andExpect(jsonPath("$[0].playerId").value(playerId));
    }

    @Test
    void rejectsCreationForUnknownPlayer() throws Exception {
        CharacterSheetCreateRequest request = new CharacterSheetCreateRequest(
                new CharacterDto("Boromir Character", HUMAN_RACE, Sexo.MASCULINO, null, 3, null, ActionProfile.IMPULSIVO, null, null, null, null),
                UUID.randomUUID().toString());

        mockMvc.perform(post("/api/character-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsCreationWithBlankCharacterName() throws Exception {
        CharacterSheetCreateRequest request = new CharacterSheetCreateRequest(
                new CharacterDto("", HUMAN_RACE, Sexo.MASCULINO, null, 3, null, ActionProfile.IMPULSIVO, null, null, null, null), playerId);

        mockMvc.perform(post("/api/character-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void persistsAndReturnsAStatefulMesticoRace() throws Exception {
        RaceDto agastias = new RaceDto(
                "AGASTIAS", "HUMAN", "VULCANO", null, List.of("DOM_BARDICO"), List.of("SOBRE_HUMANO"));
        CharacterDto character = new CharacterDto(
                "Vulcan Character", agastias, Sexo.MASCULINO, null, 5, null, ActionProfile.IMPULSIVO, null, null, null, null);
        CharacterSheetCreateRequest createRequest = new CharacterSheetCreateRequest(character, playerId);

        String createResponse = mockMvc.perform(post("/api/character-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.character.race.type").value("AGASTIAS"))
                .andExpect(jsonPath("$.character.race.parentRaceType").value("HUMAN"))
                .andExpect(jsonPath("$.character.race.linhagem").value("VULCANO"))
                .andExpect(jsonPath("$.character.race.inheritedRacialAbilities[0]").value("DOM_BARDICO"))
                .andExpect(jsonPath("$.character.race.inheritedAttributeAbilities[0]").value("SOBRE_HUMANO"))
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(createResponse).get("id").asText();

        mockMvc.perform(get("/api/character-sheets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.character.race.type").value("AGASTIAS"))
                .andExpect(jsonPath("$.character.race.parentRaceType").value("HUMAN"))
                .andExpect(jsonPath("$.character.race.linhagem").value("VULCANO"))
                .andExpect(jsonPath("$.character.race.inheritedRacialAbilities[0]").value("DOM_BARDICO"))
                .andExpect(jsonPath("$.character.race.inheritedAttributeAbilities[0]").value("SOBRE_HUMANO"));
    }

    @Test
    void rejectsAMesticoRaceWithoutAParentRaceType() throws Exception {
        RaceDto agastias = new RaceDto("AGASTIAS", null, "VULCANO", null, null, null);
        CharacterSheetCreateRequest request = new CharacterSheetCreateRequest(
                new CharacterDto("Vulcan Character", agastias, Sexo.MASCULINO, null, 3, null, ActionProfile.IMPULSIVO, null, null, null, null),
                playerId);

        mockMvc.perform(post("/api/character-sheets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
