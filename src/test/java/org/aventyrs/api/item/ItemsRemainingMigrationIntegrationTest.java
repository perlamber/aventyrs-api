package org.aventyrs.api.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.yaml.snakeyaml.Yaml;

/**
 * Liquibase 023 (core 0.1.6.7): Encaixe leaves every improvements list and a socketed stone becomes its own
 * POWER_STONE inventory entry. Liquibase already ran on the empty database at startup, so this replays changeset 001's
 * command against a seeded legacy sheet.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ItemsRemainingMigrationIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    @SuppressWarnings("unchecked")
    void aSocketedStoneMovesToTheInventoryAndEncaixeIsStripped() throws Exception {
        mongoTemplate.getCollection("characterSheets").insertOne(Document.parse("""
                {
                  "_id": "legacy-sheet",
                  "inventory": [
                    { "name": "Peitoral", "category": "ARMOR",
                      "improvements": [ { "definition": "ENCAIXE" }, { "definition": "RESISTENTE" } ],
                      "powerStone": { "type": "RUTILO_SUBTERRANEO", "quality": "JOIA" } }
                  ],
                  "character": {
                    "name": "Legado",
                    "equipment": [
                      { "name": "Lâmina", "category": "LIGHT_BLADE", "improvements": [ { "definition": "ENCAIXE" } ],
                        "powerStone": { "type": "SOMBRA_SOLIDIFICADA", "quality": "JOLDA",
                                        "improvement": "CONEXAO_VELOZ" } }
                    ]
                  }
                }
                """));

        mongoTemplate.executeCommand(Document.parse(changeSetCommand(0)));

        Document sheet = mongoTemplate.getCollection("characterSheets").find(new Document("_id", "legacy-sheet")).first();
        List<Document> inventory = (List<Document>) sheet.get("inventory");
        assertEquals(3, inventory.size());
        assertEquals(List.of("RESISTENTE"), ((List<Document>) inventory.get(0).get("improvements")).stream()
                .map(improvement -> improvement.getString("definition")).toList());
        assertNull(inventory.get(0).get("powerStone"));
        assertEquals("POWER_STONE", inventory.get(1).getString("category"));
        assertEquals("RARE", inventory.get(1).getString("rarity"));
        assertEquals("RUTILO_SUBTERRANEO", ((Document) inventory.get(1).get("powerStone")).getString("type"));
        // An Aprimoramento needs an Obra-Prima now: a bare stone keeps none.
        assertNull(((Document) inventory.get(2).get("powerStone")).get("improvement"));

        Document blade = ((List<Document>) ((Document) sheet.get("character")).get("equipment")).get(0);
        assertEquals(List.of(), blade.get("improvements"));
        assertNull(blade.get("powerStone"));
    }

    @SuppressWarnings("unchecked")
    private static String changeSetCommand(final int index) throws Exception {
        try (InputStream in = ItemsRemainingMigrationIntegrationTest.class.getResourceAsStream(
                "/db/changelog/023-items-remaining.yaml")) {
            Map<String, Object> root = new Yaml().load(in);
            List<Map<String, Object>> log = (List<Map<String, Object>>) root.get("databaseChangeLog");
            Map<String, Object> changeSet = (Map<String, Object>) log.get(index).get("changeSet");
            Map<String, Object> change = ((List<Map<String, Object>>) changeSet.get("changes")).get(0);
            return (String) ((Map<String, Object>) change.get("runCommand")).get("command");
        }
    }
}
