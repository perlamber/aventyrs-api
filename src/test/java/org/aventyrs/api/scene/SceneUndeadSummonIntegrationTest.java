package org.aventyrs.api.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.aventyrs.api.monster.MonsterSheetDocument;
import org.aventyrs.api.monster.MonsterSheetRepository;
import org.aventyrs.api.monster.MonsterSheetService;
import org.aventyrs.api.monster.SummonEntry;
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
import org.aventyrs.core.character.Character.Sexo;
import org.aventyrs.core.sheet.IllegalOperationException;
import org.aventyrs.core.util.DiceRoller;
import org.aventyrs.core.util.TranslatableMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

/** The REANIMAR tree in a persisted Scene (core 0.1.6.24–0.1.6.26). */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SceneUndeadSummonIntegrationTest {

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
    private String fallen;
    private String sceneId;

    @BeforeEach
    void setup() {
        playerId = playerService.create(new PlayerRequest("Necro Player", "necro-" + UUID.randomUUID())).id();
        caster = characterSheet(playerId);
        fallen = characterSheet(playerId);
        sceneId = sceneService.create(new SceneCreateRequest("Crypt", "FOREST", 100, 100)).id();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(caster, 20, UUID.randomUUID()));
        sceneService.addParticipant(sceneId, new AddParticipantRequest(fallen, 10, UUID.randomUUID()));
        sceneService.startCombat(sceneId);
        sceneService.advanceTurn(sceneId); // the caster's Turn
        dice(DiceRoller.fixed(1));
    }

    private void dice(final DiceRoller roller) {
        Object target = AopTestUtils.getUltimateTargetObject(sceneService);
        ReflectionTestUtils.setField(target, "diceRoller", roller);
    }

    private String characterSheet(String owner) {
        return characterSheetService.create(new CharacterSheetCreateRequest(
                new CharacterDto("Necromante", new RaceDto("HUMAN", null, null, null, null, null),
                        Sexo.MASCULINO, null, 6, null, ActionProfile.IMPULSIVO, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null),
                owner)).id();
    }

    private SceneParticipantResponse raise(String corpse) {
        return sceneService.addSummon(sceneId, new SummonCreateRequest(UUID.randomUUID().toString(), caster, "ZUMBI",
                3, List.of(), null, 2, true, null, null, null, corpse, "PLUS_ONE", false));
    }

    private SceneParticipantResponse participant(String id) {
        return sceneService.get(sceneId).participants().stream()
                .filter(entry -> entry.characterSheetId().equals(id))
                .findFirst().orElseThrow();
    }

    private List<String> participantIds() {
        return sceneService.get(sceneId).participants().stream().map(SceneParticipantResponse::characterSheetId).toList();
    }

    @Test
    void raisingReplacesTheCorpseTokenAndCarriesTheCount() {
        SceneParticipantResponse corpse = participant(fallen);

        SceneParticipantResponse zumbi = raise(fallen);

        assertFalse(participantIds().contains(fallen), "the corpse token leaves");
        assertEquals(fallen, characterSheetService.get(fallen).id(), "a PC's sheet is kept");
        assertEquals(corpse.position(), zumbi.position(), "the undead takes its square");
        MonsterSheetDocument sheet = monsterSheetRepository.findById(zumbi.characterSheetId()).orElseThrow();
        assertEquals(1, sheet.getTimesRaised());
        assertEquals("ZUMBI", sheet.getSummon().kind());
        assertEquals("PLUS_ONE", sheet.getSummon().bodySize());
        assertTrue(sceneService.get(sceneId).summons().get(0).raisedFromCorpse());
    }

    @Test
    void aFallenRaisedZumbiLeavesItsCorpseAndCanBeRaisedAgain() {
        String zumbi = raise(fallen).characterSheetId();

        sceneService.dismissSummon(sceneId, zumbi);

        assertTrue(participantIds().contains(zumbi), "the body stays");
        assertTrue(sceneService.get(sceneId).summons().isEmpty());
        assertTrue(monsterSheetRepository.findById(zumbi).orElseThrow().getCorpse());

        String again = raise(zumbi).characterSheetId();
        assertFalse(monsterSheetRepository.existsById(zumbi), "the old body is spent into the new one");
        assertEquals(2, monsterSheetRepository.findById(again).orElseThrow().getTimesRaised());
    }

    @Test
    void aBodyRaisedFourTimesIsRefused() {
        String worn = UUID.randomUUID().toString();
        monsterSheetService.createSummon(worn, new SummonEntry("ZUMBI", 0, List.of(), caster), playerId, 4);
        monsterSheetService.markCorpse(worn);
        sceneService.addParticipant(sceneId, new AddParticipantRequest(worn, 5, UUID.randomUUID()));

        IllegalOperationException refused = assertThrows(IllegalOperationException.class, () -> raise(worn));
        assertEquals(TranslatableMessages.CORPSE_EXHAUSTED, refused.getMessage());
    }

    @Test
    void festimRaisesPastItsLimitUncontrolledAndTakesThemAlong() {
        dice(DiceRoller.fixed(2, 6, 4));
        sceneService.addSummonSpawner(sceneId, new SummonSpawnerCreateRequest(caster, 3, null, 3, List.of(), null,
                null, SceneSummonSpawnerEntry.FESTIM, 1));

        SceneResponse scene = sceneService.get(sceneId);
        SceneSummonEntry first = scene.summons().get(0);
        assertFalse(first.uncontrolled(), "within Foco 1");
        assertEquals(scene.currentRound() + 1, participant(first.summonId()).joinedAtRound(), "acts next Rodada");
        assertEquals(2, scene.summonSpawners().get(0).lastFace());

        toNextRodada();
        SceneSummonEntry second = sceneService.get(sceneId).summons().get(1);
        assertTrue(second.uncontrolled(), "past the limit");
        assertEquals("ABANTESMA", monsterSheetRepository.findById(second.summonId()).orElseThrow().getSummon().kind());
        assertTrue(monsterSheetRepository.findById(second.summonId()).orElseThrow().getSummon().uncontrolled(),
                "every client reads it as a foe");
        assertNotEquals(participant(caster).group(), participant(second.summonId()).group(), "a hostile group");
        assertNotEquals(playerId, monsterSheetRepository.findById(second.summonId()).orElseThrow().getPlayerId());

        while (!sceneService.get(sceneId).summonSpawners().isEmpty()) {
            sceneService.advanceTurnReporting(sceneId);
        }
        assertTrue(sceneService.get(sceneId).summons().isEmpty(), "they leave with the sarcophagus");
    }

    @Test
    void hordaFamintaControlsNone() {
        sceneService.addSummonSpawner(sceneId, new SummonSpawnerCreateRequest(caster, 3, null, 3, List.of(), null,
                null, SceneSummonSpawnerEntry.FESTIM_HORDA, 5));

        List<SceneSummonEntry> risen = sceneService.get(sceneId).summons();
        assertEquals(2, risen.size());
        assertTrue(risen.stream().allMatch(SceneSummonEntry::uncontrolled));
    }

    @Test
    void theCuspidorsCarrascosGoWhenTheCombatEnds() {
        sceneService.addSummonSpawner(sceneId, new SummonSpawnerCreateRequest(caster, 0, null, 0, List.of(), null,
                null, SceneSummonSpawnerEntry.CUSPIDOR, null));
        List<SceneSummonEntry> spat = sceneService.get(sceneId).summons();
        assertEquals(2, spat.size());
        assertEquals("CARRASCO_DEVIANTE",
                monsterSheetRepository.findById(spat.get(0).summonId()).orElseThrow().getSummon().kind());

        sceneService.endCombat(sceneId);

        assertTrue(sceneService.get(sceneId).summons().isEmpty());
        assertTrue(sceneService.get(sceneId).summonSpawners().isEmpty());
    }

    @Test
    void anAbantesmaStruckLosesARodada() {
        String ghost = sceneService.addSummon(sceneId, new SummonCreateRequest(UUID.randomUUID().toString(), caster,
                "ABANTESMA", 3, List.of(), null, 2, false, null)).characterSheetId();

        assertFalse(sceneService.shortenSummon(sceneId, ghost));
        assertEquals(1, sceneService.get(sceneId).summons().get(0).remainingRounds());
        assertTrue(sceneService.shortenSummon(sceneId, ghost), "spent");
        assertFalse(participantIds().contains(ghost));
    }

    private void toNextRodada() {
        int round = sceneService.get(sceneId).currentRound();
        while (sceneService.get(sceneId).currentRound() == round) {
            sceneService.advanceTurnReporting(sceneId);
        }
    }
}
