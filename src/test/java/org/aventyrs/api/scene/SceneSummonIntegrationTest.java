package org.aventyrs.api.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.aventyrs.api.monster.MonsterSheetRepository;
import org.aventyrs.api.monster.MonsterSheetService;
import org.aventyrs.api.player.PlayerService;
import org.aventyrs.api.player.dto.PlayerRequest;
import org.aventyrs.api.scene.dto.AddParticipantRequest;
import org.aventyrs.api.scene.dto.SceneCreateRequest;
import org.aventyrs.api.scene.dto.SceneParticipantResponse;
import org.aventyrs.api.scene.dto.SceneResponse;
import org.aventyrs.api.scene.dto.SummonCreateRequest;
import org.aventyrs.api.scene.dto.SummonSpawnerCreateRequest;
import org.aventyrs.api.sheet.CharacterSheetService;
import org.aventyrs.api.sheet.dto.CharacterDto;
import org.aventyrs.api.sheet.dto.CharacterSheetCreateRequest;
import org.aventyrs.api.sheet.dto.RaceDto;
import org.aventyrs.core.action.ActionProfile;
import org.aventyrs.core.character.Alignment;
import org.aventyrs.core.character.Character.Sexo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Invocations in a persisted Scene (client 0.0.95) — the server's mirror of core's {@code Scene#addSummons}. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SceneSummonIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0").withReplicaSet();

    @Autowired
    private SceneService sceneService;

    @Autowired
    private PlayerService playerService;

    @Autowired
    private CharacterSheetService characterSheetService;

    @Autowired
    private MonsterSheetService monsterSheetService;

    @Autowired
    private MonsterSheetRepository monsterSheetRepository;

    private String playerId;
    private String caster;
    private String foe;
    private String sceneId;

    @BeforeEach
    void setup() {
        playerId = playerService.create(new PlayerRequest("Druid Player", "druid-" + UUID.randomUUID())).id();
        caster = characterSheet(playerId);
        foe = characterSheet(playerId);
        sceneId = sceneService.create(new SceneCreateRequest("Glade", "FOREST", 100, 100)).id();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(caster, 20, UUID.randomUUID()));
        sceneService.addParticipant(sceneId, new AddParticipantRequest(foe, 10, UUID.randomUUID()));
        sceneService.startCombat(sceneId);
        sceneService.advanceTurn(sceneId); // the caster's Turn
    }

    private String characterSheet(String owner) {
        return characterSheetService.create(new CharacterSheetCreateRequest(
                new CharacterDto("Druida", new RaceDto("HUMAN", null, null, null, null, null),
                        Sexo.MASCULINO, null, Alignment.NEUTRAL, null, ActionProfile.IMPULSIVO, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null),
                owner)).id();
    }

    private SceneParticipantResponse summon(String group, Integer rounds, boolean concentration) {
        return sceneService.addSummon(sceneId, new SummonCreateRequest(UUID.randomUUID().toString(), caster,
                "ALIADO_DA_NATUREZA", 4, List.of(), group, rounds, concentration, null));
    }

    private List<String> order() {
        return sceneService.get(sceneId).participants().stream().map(SceneParticipantResponse::characterSheetId).toList();
    }

    /** Its own sheet, run by the caster's player, standing right after the caster. */
    @Test
    void aSummonJoinsAfterItsCasterAsTheCastersPlayers() {
        String summonId = summon("ALIADOS_DA_NATUREZA", 3, false).characterSheetId();

        assertEquals(List.of(caster, summonId, foe), order());
        var sheet = monsterSheetService.get(summonId);
        assertEquals(playerId, sheet.playerId());
        assertEquals("ALIADO_DA_NATUREZA", sheet.summon().kind());
        assertNull(sheet.blueprint());
        assertEquals(20, sheet.maxHitPoints(), "Graduação 4: 15PV + 5");
    }

    @Test
    void aSummonLeavesAtTheRodadaBoundaryThatSpendsItsDuracao() {
        String summonId = summon(null, 1, false).characterSheetId();
        sceneService.advanceTurn(sceneId); // the summon
        sceneService.advanceTurn(sceneId); // the foe

        SceneService.TurnAdvance wrap = sceneService.advanceTurnReporting(sceneId);

        assertTrue(wrap.rosterChanged());
        assertFalse(order().contains(summonId));
        assertFalse(monsterSheetRepository.existsById(summonId));
        assertEquals(caster, wrap.event().characterSheetId());
    }

    /** "Caso um novo … seja invocado o anterior desaparece ao final do Turno." */
    @Test
    void aNewerSummonOfTheGroupReplacesTheOlderWhenTheCastersTurnEnds() {
        String first = summon("ALIADOS_DA_NATUREZA", 3, false).characterSheetId();
        String second = summon("ALIADOS_DA_NATUREZA", 3, false).characterSheetId();
        assertTrue(order().contains(first));

        SceneService.TurnAdvance advance = sceneService.advanceTurnReporting(sceneId);

        assertTrue(advance.rosterChanged());
        assertEquals(List.of(caster, second, foe), order());
        assertEquals(second, advance.event().characterSheetId());
    }

    /** "Concentração + 2": no countdown until the caster's focus breaks. */
    @Test
    void aConcentracaoSummonCountsDownOnlyOnceReleased() {
        String summonId = summon(null, 2, true).characterSheetId();
        SceneResponse scene = sceneService.get(sceneId);
        assertNull(scene.summons().get(0).remainingRounds());

        assertTrue(sceneService.releaseConcentration(sceneId, caster));
        assertEquals(2, sceneService.get(sceneId).summons().get(0).remainingRounds());
        assertFalse(sceneService.releaseConcentration(sceneId, caster), "nothing left to release");
        assertTrue(order().contains(summonId));
    }

    @Test
    void dismissingASummonRemovesItsSheetAndACasterLeavingTakesItsSummons() {
        String fallen = summon(null, 3, false).characterSheetId();
        String other = summon(null, 3, false).characterSheetId();

        sceneService.dismissSummon(sceneId, fallen);
        assertFalse(order().contains(fallen));
        assertFalse(monsterSheetRepository.existsById(fallen));

        sceneService.removeParticipant(sceneId, caster);
        assertEquals(List.of(foe), order());
        assertFalse(monsterSheetRepository.existsById(other));
        assertTrue(sceneService.get(sceneId).summons().isEmpty());
    }

    /** Totem de Gaea: a Predador now, and one more at each Rodada boundary while it stands. */
    @Test
    void aSpawnerInvokesEachRodada() {
        sceneService.addSummonSpawner(sceneId, new SummonSpawnerCreateRequest(caster, 3, "PREDADOR_REGIONAL", 4,
                List.of(), 3));
        assertEquals(1, sceneService.get(sceneId).summons().size());

        int round = sceneService.get(sceneId).currentRound();
        while (sceneService.get(sceneId).currentRound() == round) {
            sceneService.advanceTurn(sceneId);
        }

        assertEquals(2, sceneService.get(sceneId).summons().size());
    }
}
