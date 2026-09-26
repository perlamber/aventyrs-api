package org.aventyrs.api.campaign;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.aventyrs.api.campaign.dto.BagAddRequest;
import org.aventyrs.api.campaign.dto.BagClaimRequest;
import org.aventyrs.api.campaign.dto.BagDepositRequest;
import org.aventyrs.api.campaign.dto.BagLootRequest;
import org.aventyrs.api.campaign.dto.CampaignCreateRequest;
import org.aventyrs.api.item.dto.InventoryItemDto;
import org.aventyrs.api.monster.MonsterSheetService;
import org.aventyrs.api.monster.dto.MonsterBlueprintDto;
import org.aventyrs.api.monster.dto.MonsterSheetCreateRequest;
import org.aventyrs.api.player.PlayerService;
import org.aventyrs.api.player.dto.PlayerRequest;
import org.aventyrs.api.sheet.CharacterSheetService;
import org.aventyrs.api.sheet.dto.CharacterDto;
import org.aventyrs.api.sheet.dto.CharacterSheetCreateRequest;
import org.aventyrs.api.sheet.dto.RaceDto;
import org.aventyrs.core.action.ActionProfile;
import org.aventyrs.core.character.Alignment;
import org.aventyrs.core.character.Character.Sexo;
import org.aventyrs.core.item.ItemCategory;
import org.aventyrs.core.item.ItemRarity;
import org.aventyrs.core.item.ItemWeightClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import tools.jackson.databind.ObjectMapper;

/** The Campanha's shared bag: GM loot, Saquear, claims and deposits. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CampaignBagIntegrationTest {

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
    private MonsterSheetService monsterSheetService;

    private String playerId;
    private String sheetId;
    private String campaignId;

    @BeforeEach
    void createCampaignWithAParticipant() throws Exception {
        playerId = playerService.create(new PlayerRequest("Bag Player", "bag-player-" + UUID.randomUUID())).id();
        sheetId = newSheet("Saqueadora");
        campaignId = objectMapper.readTree(mockMvc.perform(post("/api/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CampaignCreateRequest("Crônicas"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bag", empty()))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(put("/api/campaigns/{id}/participants/{sheetId}", campaignId, sheetId))
                .andExpect(status().isOk());
    }

    private String newSheet(String name) {
        return characterSheetService.create(new CharacterSheetCreateRequest(
                new CharacterDto(name, new RaceDto("HUMAN", null, null, null, null, null),
                        Sexo.FEMININO, null, Alignment.NEUTRAL, null, ActionProfile.IMPULSIVO, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null),
                playerId)).id();
    }

    private String newGoblin(List<InventoryItemDto> inventory) {
        // The least legal blueprint: a name, GP 0, every Atributo at base 1.
        MonsterBlueprintDto goblin = new MonsterBlueprintDto("Goblin", 0, null, null, null, null, null, null,
                null, null, null, null, null, null, null, 0, 0);
        return monsterSheetService.create(new MonsterSheetCreateRequest(goblin, playerId, null, inventory)).id();
    }

    private static InventoryItemDto item(String name) {
        return new InventoryItemDto(null, name, null, ItemCategory.ARMOR, ItemRarity.COMMON,
                ItemWeightClass.HEAVY, 0, 1, 0, 0, 0, 0, null, null, List.of(), null, null, null, false);
    }

    private ResultActions postJson(String path, Object body, Object... vars) throws Exception {
        return mockMvc.perform(post(path, vars)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private String firstEntryId(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString())
                .get("bag").get(0).get("id").asText();
    }

    @Test
    void theGmAddsLootAndAParticipantClaimsIt() throws Exception {
        String entryId = firstEntryId(postJson("/api/campaigns/{id}/bag", new BagAddRequest(item("Elmo"), null), campaignId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bag", hasSize(1)))
                .andExpect(jsonPath("$.bag[0].item.name").value("Elmo")));

        postJson("/api/campaigns/{id}/bag/{entryId}/claim", new BagClaimRequest(sheetId), campaignId, entryId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bag", empty()));
        mockMvc.perform(get("/api/character-sheets/{id}", sheetId))
                .andExpect(jsonPath("$.inventory", hasSize(1)))
                .andExpect(jsonPath("$.inventory[0].name").value("Elmo"));

        // A second claim of the same entry finds it gone.
        postJson("/api/campaigns/{id}/bag/{entryId}/claim", new BagClaimRequest(sheetId), campaignId, entryId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(CampaignService.BAG_ITEM_NOT_FOUND));
    }

    @Test
    void onlyAParticipantMayClaim() throws Exception {
        String entryId = firstEntryId(postJson("/api/campaigns/{id}/bag", new BagAddRequest(item("Elmo"), null), campaignId));

        postJson("/api/campaigns/{id}/bag/{entryId}/claim", new BagClaimRequest(newSheet("Estranha")), campaignId, entryId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(CampaignService.NOT_A_CAMPAIGN_PARTICIPANT));
        mockMvc.perform(get("/api/campaigns/{id}", campaignId))
                .andExpect(jsonPath("$.bag", hasSize(1)));
    }

    @Test
    void aSaquearMovesEverythingTheFoeCarries() throws Exception {
        String goblinId = newGoblin(List.of(item("Adaga"), item("Capa")));

        postJson("/api/campaigns/{id}/bag/loot", new BagLootRequest(goblinId), campaignId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bag", hasSize(2)))
                .andExpect(jsonPath("$.bag[0].sourceName").value("Goblin"))
                .andExpect(jsonPath("$.bag[1].item.name").value("Capa"));
        mockMvc.perform(get("/api/monster-sheets/{id}", goblinId))
                .andExpect(jsonPath("$.inventory", empty()));

        // Looting it again finds nothing, so a client knows not to charge the 2PA.
        postJson("/api/campaigns/{id}/bag/loot", new BagLootRequest(goblinId), campaignId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(CampaignService.NOTHING_TO_LOOT));
    }

    @Test
    void aParticipantCannotBeLooted() throws Exception {
        postJson("/api/campaigns/{id}/bag", new BagAddRequest(item("Elmo"), null), campaignId);
        String entryId = firstEntryId(postJson("/api/campaigns/{id}/bag", new BagAddRequest(item("Botas"), null), campaignId));
        postJson("/api/campaigns/{id}/bag/{entryId}/claim", new BagClaimRequest(sheetId), campaignId, entryId);

        postJson("/api/campaigns/{id}/bag/loot", new BagLootRequest(sheetId), campaignId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("LOOT_TARGET_NOT_AN_ENEMY"));
    }

    @Test
    void aDepositNamesTheItemItMoves() throws Exception {
        postJson("/api/campaigns/{id}/bag/loot", new BagLootRequest(newGoblin(List.of(item("Adaga"), item("Capa")))), campaignId);
        String campaign = mockMvc.perform(get("/api/campaigns/{id}", campaignId)).andReturn().getResponse().getContentAsString();
        for (var entry : objectMapper.readTree(campaign).get("bag")) {
            postJson("/api/campaigns/{id}/bag/{entryId}/claim", new BagClaimRequest(sheetId), campaignId, entry.get("id").asText())
                    .andExpect(status().isOk());
        }

        // A stale index: the name at 0 is Adaga, not Capa.
        postJson("/api/campaigns/{id}/bag/deposit", new BagDepositRequest(sheetId, 0, "Capa"), campaignId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(CampaignService.INVENTORY_ITEM_NOT_FOUND));

        postJson("/api/campaigns/{id}/bag/deposit", new BagDepositRequest(sheetId, 1, "Capa"), campaignId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bag", hasSize(1)))
                .andExpect(jsonPath("$.bag[0].item.name").value("Capa"))
                .andExpect(jsonPath("$.bag[0].sourceName").doesNotExist());
        mockMvc.perform(get("/api/character-sheets/{id}", sheetId))
                .andExpect(jsonPath("$.inventory", hasSize(1)))
                .andExpect(jsonPath("$.inventory[0].name").value("Adaga"));
    }

    @Test
    void theGmDiscardsAnEntry() throws Exception {
        String entryId = firstEntryId(postJson("/api/campaigns/{id}/bag", new BagAddRequest(item("Elmo"), "Baú"), campaignId)
                .andExpect(jsonPath("$.bag[0].sourceName").value("Baú")));

        mockMvc.perform(delete("/api/campaigns/{id}/bag/{entryId}", campaignId, entryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bag", empty()));
        mockMvc.perform(delete("/api/campaigns/{id}/bag/{entryId}", campaignId, entryId))
                .andExpect(status().isConflict());
    }

    /** Every bag change re-reads under the version, so a flurry of writes loses nothing. */
    @Test
    void concurrentAdditionsAreAllKept() throws Exception {
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            String name = "Item " + i;
            threads.add(Thread.ofVirtual().start(() -> {
                try {
                    postJson("/api/campaigns/{id}/bag", new BagAddRequest(item(name), null), campaignId);
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            }));
        }
        for (Thread thread : threads) {
            thread.join();
        }
        mockMvc.perform(get("/api/campaigns/{id}", campaignId))
                .andExpect(jsonPath("$.bag", hasSize(4)));
    }
}
