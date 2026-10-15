package org.aventyrs.api.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.aventyrs.api.monster.MonsterSheetControllerIntegrationTest;
import org.aventyrs.api.monster.MonsterSheetRepository;
import org.aventyrs.api.monster.MonsterSheetService;
import org.aventyrs.api.monster.dto.ConjurePreviewRequest;
import org.aventyrs.api.monster.dto.MonsterSheetCreateRequest;
import org.aventyrs.api.player.PlayerRole;
import org.aventyrs.api.player.dto.PlayerRequest;
import org.aventyrs.api.scene.dto.AddParticipantRequest;
import org.aventyrs.api.scene.dto.ConjureRequest;
import org.aventyrs.api.scene.dto.GridPositionDto;
import org.aventyrs.api.scene.dto.SceneCreateRequest;
import org.aventyrs.core.character.CharacterStatus;
import org.aventyrs.core.monster.MonsterKind;
import org.aventyrs.core.monster.generator.ConjurePreset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Conjured foes, the campaign-scoped Bestiário and one-Cena-at-a-time placement (core 0.1.7.5). */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@WithMockUser(roles = "GM")
class SceneConjureIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MonsterSheetRepository monsterSheetRepository;

    @Autowired
    private MonsterSheetService monsterSheetService;

    private String gmId;

    @BeforeEach
    void createGm() throws Exception {
        gmId = idOf(mockMvc.perform(post("/api/players").with(user("gm").roles("GM")).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new PlayerRequest("Narrador", "gm-" + UUID.randomUUID(), PlayerRole.GM))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    // ---- conjure -------------------------------------------------------------------------------

    @Test
    void conjuresTheCountAroundTheCentreWithoutOverlapAndHidesThemFromTheBestiary() throws Exception {
        String sceneId = createScene(10, 10);
        JsonNode added = conjure(sceneId, request(3, new GridPositionDto(4, 4), null));

        assertEquals(3, added.size());
        Set<String> cells = new HashSet<>();
        List<String> ids = new ArrayList<>();
        for (JsonNode participant : added) {
            JsonNode position = participant.get("position");
            cells.add(position.get("x").asInt() + "," + position.get("y").asInt());
            ids.add(participant.get("characterSheetId").asText());
            assertTrue(Math.abs(position.get("x").asInt() - 4) <= 1 && Math.abs(position.get("y").asInt() - 4) <= 1,
                    "copies fill the ring around the centre");
        }
        assertEquals(3, cells.size(), "no two copies share a hex");
        assertTrue(cells.contains("4,4"), "the first copy stands on the free centre");

        JsonNode first = sheet(ids.get(0));
        assertTrue(first.get("conjured").asBoolean());
        assertEquals(sceneId, first.get("conjuredInSceneId").asText());
        assertEquals("bundled:conjured/mutante_monstruoso.png", first.get("tokenImageUrl").asText());
        assertFalse(first.get("playerId").isNull(), "owned by the table's GM");
        assertEquals("Rato 2", sheet(ids.get(1)).get("blueprint").get("name").asText());

        Set<String> bestiary = idsOf(get("/api/monster-sheets/bestiary"));
        ids.forEach(id -> assertFalse(bestiary.contains(id)));
    }

    @Test
    void anOccupiedCentreFallsToTheNearestFreeHex() throws Exception {
        String sceneId = createScene(10, 10);
        conjure(sceneId, request(1, new GridPositionDto(0, 0), null));

        JsonNode second = conjure(sceneId, request(1, new GridPositionDto(0, 0), null)).get(0).get("position");

        assertNotEquals("0,0", second.get("x").asInt() + "," + second.get("y").asInt());
        assertTrue(second.get("x").asInt() <= 1 && second.get("y").asInt() <= 1);
    }

    @Test
    void aCentreOutsideTheSceneOrABadCountIsRefused() throws Exception {
        String sceneId = createScene(5, 5);
        mockMvc.perform(post("/api/scenes/{id}/conjure", sceneId).contentType(MediaType.APPLICATION_JSON)
                        .content(json(request(1, new GridPositionDto(7, 1), null))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/scenes/{id}/conjure", sceneId).contentType(MediaType.APPLICATION_JSON)
                        .content(json(request(0, new GridPositionDto(1, 1), null))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/scenes/{id}/conjure", sceneId).contentType(MediaType.APPLICATION_JSON)
                        .content(json(request(1, null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "PLAYER")
    void onlyTheGmConjures() throws Exception {
        mockMvc.perform(post("/api/scenes/{id}/conjure", "any").contentType(MediaType.APPLICATION_JSON)
                        .content(json(request(1, new GridPositionDto(1, 1), null))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/monster-sheets/generate-preview").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ConjurePreviewRequest(ConjurePreset.BESTA_ALADA, 5, null, null, 1L))))
                .andExpect(status().isForbidden());
    }

    @Test
    void thePreviewIsTheMonsterTheConjureStores() throws Exception {
        String sceneId = createScene(10, 10);
        JsonNode preview = objectMapper.readTree(mockMvc.perform(post("/api/monster-sheets/generate-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ConjurePreviewRequest(ConjurePreset.MUTANTE_MONSTRUOSO, 12, null, "Rato", 7L))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String stored = conjure(sceneId, request(1, new GridPositionDto(2, 2), null)).get(0)
                .get("characterSheetId").asText();

        assertEquals(preview.get("blueprint"), sheet(stored).get("blueprint"));
        assertEquals(preview.get("maxHitPoints"), sheet(stored).get("maxHitPoints"));
    }

    @Test
    void aConjuredFoeLeavingItsSceneIsDeletedAndSoAreTheSceneOnesWhenTheSceneGoes() throws Exception {
        String sceneId = createScene(10, 10);
        JsonNode added = conjure(sceneId, request(2, new GridPositionDto(3, 3), null));
        String leaving = added.get(0).get("characterSheetId").asText();
        String staying = added.get(1).get("characterSheetId").asText();

        mockMvc.perform(delete("/api/scenes/{id}/participants/{sheet}", sceneId, leaving))
                .andExpect(status().isNoContent());
        assertFalse(monsterSheetRepository.existsById(leaving));

        mockMvc.perform(delete("/api/scenes/{id}", sceneId)).andExpect(status().isNoContent());
        assertFalse(monsterSheetRepository.existsById(staying));
    }

    // ---- Bestiário and placement ---------------------------------------------------------------

    @Test
    void theBestiaryFiltersByCampaignAndLore() throws Exception {
        String campaign = "campaign-" + UUID.randomUUID();
        String lore = createFoe(null);
        String own = createFoe(campaign);
        String other = createFoe("campaign-" + UUID.randomUUID());

        Set<String> ofCampaign = idsOf(get("/api/monster-sheets/bestiary").param("campaign", campaign));
        assertEquals(Set.of(own), ofCampaign);

        Set<String> loreOnly = idsOf(get("/api/monster-sheets/bestiary").param("campaign", "lore"));
        assertTrue(loreOnly.contains(lore));
        assertFalse(loreOnly.contains(own));

        Set<String> all = idsOf(get("/api/monster-sheets/bestiary"));
        assertTrue(all.containsAll(Set.of(lore, own, other)));
    }

    @Test
    void placeableOffersCampaignAndLoreFoesNotFallenAndNotInAnotherScene() throws Exception {
        String campaign = "campaign-" + UUID.randomUUID();
        String lore = createFoe(null);
        String own = createFoe(campaign);
        String foreign = createFoe("campaign-" + UUID.randomUUID());
        String fallen = createFoe(campaign);
        monsterSheetService.updateCombatStatus(fallen, 0, 0, 0, CharacterStatus.DEAD, null);
        String elsewhere = createFoe(campaign);

        String sceneA = createScene(10, 10);
        String sceneB = createScene(10, 10);
        addParticipant(sceneA, elsewhere).andExpect(status().isCreated());

        Set<String> forB = idsOf(get("/api/monster-sheets/placeable").param("campaignId", campaign).param("sceneId", sceneB));
        assertTrue(forB.containsAll(Set.of(lore, own)));
        assertFalse(forB.contains(foreign));
        assertFalse(forB.contains(fallen));
        assertFalse(forB.contains(elsewhere), "a foe standing in another Cena");

        Set<String> forA = idsOf(get("/api/monster-sheets/placeable").param("campaignId", campaign).param("sceneId", sceneA));
        assertTrue(forA.contains(elsewhere), "its own Cena still lists it");

        Set<String> loreOnly = idsOf(get("/api/monster-sheets/placeable").param("sceneId", sceneB));
        assertTrue(loreOnly.contains(lore));
        assertFalse(loreOnly.contains(own));

        // Still in the Bestiário while placed.
        assertTrue(idsOf(get("/api/monster-sheets/bestiary")).contains(elsewhere));
    }

    @Test
    void aBestiaryFoeStandsInOneSceneAtATime() throws Exception {
        String foe = createFoe(null);
        String sceneA = createScene(10, 10);
        String sceneB = createScene(10, 10);
        addParticipant(sceneA, foe).andExpect(status().isCreated());

        addParticipant(sceneB, foe).andExpect(status().isConflict());

        mockMvc.perform(delete("/api/scenes/{id}/participants/{sheet}", sceneA, foe)).andExpect(status().isNoContent());
        assertTrue(monsterSheetRepository.existsById(foe), "a Bestiário foe is never deleted by leaving");
        addParticipant(sceneB, foe).andExpect(status().isCreated());
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static ConjureRequest request(int count, GridPositionDto centre, String campaignId) {
        return new ConjureRequest(ConjurePreset.MUTANTE_MONSTRUOSO, 12, MonsterKind.REGULAR, "Rato", count, 7L,
                UUID.randomUUID(), null, campaignId, centre, null);
    }

    private JsonNode conjure(String sceneId, ConjureRequest request) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/scenes/{id}/conjure", sceneId)
                        .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String createScene(int width, int height) throws Exception {
        return idOf(mockMvc.perform(post("/api/scenes").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SceneCreateRequest("Clareira " + UUID.randomUUID(), "FOREST", width, height))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String createFoe(String campaignId) throws Exception {
        return idOf(mockMvc.perform(post("/api/monster-sheets").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new MonsterSheetCreateRequest(
                                MonsterSheetControllerIntegrationTest.goblin("Goblin " + UUID.randomUUID()),
                                gmId, null, List.of(), campaignId))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private org.springframework.test.web.servlet.ResultActions addParticipant(String sceneId, String sheetId)
            throws Exception {
        return mockMvc.perform(post("/api/scenes/{id}/participants", sceneId).contentType(MediaType.APPLICATION_JSON)
                .content(json(new AddParticipantRequest(sheetId, 10, UUID.randomUUID()))));
    }

    private JsonNode sheet(String id) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/monster-sheets/{id}", id))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private Set<String> idsOf(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        JsonNode list = objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        Set<String> ids = new HashSet<>();
        list.forEach(node -> ids.add(node.get("id").asText()));
        return ids;
    }

    private String idOf(String body) {
        return objectMapper.readTree(body).get("id").asText();
    }

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}
