package org.aventyrs.api.sheet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import org.aventyrs.api.player.PlayerService;
import org.aventyrs.api.player.dto.PlayerRequest;
import org.aventyrs.api.sheet.dto.CharacterDto;
import org.aventyrs.api.sheet.dto.CharacterSheetCreateRequest;
import org.aventyrs.api.sheet.dto.RaceDto;
import org.aventyrs.api.sheet.dto.SubordinateDto;
import org.aventyrs.core.action.ActionProfile;
import org.aventyrs.core.character.Alignment;
import org.aventyrs.core.character.Character.Sexo;
import org.aventyrs.core.character.CharacterStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

/** A character's Subordinados ride the live status frame and come back on the sheet (core 0.0.98). */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SubordinatePersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0").withReplicaSet();

    @Autowired
    private PlayerService playerService;

    @Autowired
    private CharacterSheetService characterSheetService;

    @Autowired
    private org.aventyrs.api.monster.MonsterSheetService monsterSheetService;

    @Test
    void subordinadosSentOnTheStatusFrameAreStoredAndAFrameWithoutThemLeavesThemAlone() {
        String playerId = playerService.create(new PlayerRequest("Orc", "orc-" + UUID.randomUUID())).id();
        String id = characterSheetService.create(new CharacterSheetCreateRequest(
                new CharacterDto("Orc", new RaceDto("HUMAN", null, null, null, null, null),
                        Sexo.MASCULINO, null, 6, null, ActionProfile.IMPULSIVO, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null),
                playerId)).id();
        SubordinateDto peao = new SubordinateDto("PEAO_SKILL", false, "AGNACAO_ANCESTRAL_SUPERIOR", null, null, null,
                null, "MINIMO", UUID.randomUUID().toString());

        characterSheetService.updateCombatStatus(id, 0, 0, 0, CharacterStatus.CLEAN, null, null, null, null, null,
                null, null, null, List.of(peao));
        assertEquals(List.of(peao), characterSheetService.get(id).subordinates());

        characterSheetService.updateCombatStatus(id, 2, 0, 0, CharacterStatus.CLEAN);
        assertEquals(List.of(peao), characterSheetService.get(id).subordinates());
    }

    /** A foe commands Subordinados too (core 0.1.5.6) — the GM's status frame stores them on its own document. */
    @Test
    void aMonstersSubordinadosAreStoredOnItsSheet() {
        String playerId = playerService.create(new PlayerRequest("Mestre", "gm-" + UUID.randomUUID())).id();
        org.aventyrs.api.monster.dto.MonsterBlueprintDto goblin = new org.aventyrs.api.monster.dto.MonsterBlueprintDto(
                "Goblin", 0, null, null, null, null, null, null, null, null, null, null, null, null, null, null, 0, 0);
        String id = monsterSheetService.create(
                new org.aventyrs.api.monster.dto.MonsterSheetCreateRequest(goblin, playerId, null, null)).id();
        assertEquals(List.of(), monsterSheetService.get(id).subordinates());

        SubordinateDto torre = new SubordinateDto("TORRE_RA", true, "Mestre", null, null, null, null, null,
                UUID.randomUUID().toString());
        monsterSheetService.updateSubordinates(id, List.of(torre));

        assertEquals(List.of(torre), monsterSheetService.get(id).subordinates());
    }
}
