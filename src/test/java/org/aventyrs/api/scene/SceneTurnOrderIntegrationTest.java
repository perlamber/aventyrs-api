package org.aventyrs.api.scene;

import org.aventyrs.api.player.PlayerService;
import org.aventyrs.api.player.dto.PlayerRequest;
import org.aventyrs.api.scene.dto.AddParticipantRequest;
import org.aventyrs.api.scene.dto.SceneCreateRequest;
import org.aventyrs.api.scene.dto.SceneParticipantResponse;
import org.aventyrs.api.scene.dto.SceneResponse;
import org.aventyrs.api.scene.dto.TurnAdvancedEvent;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The turn order across a whole table's worth of combatants: many joining before combat, more joining Rodada after
 * Rodada, and combats stopped and started again. Every advance is checked against the order's invariants
 * ({@link #assertOrderInvariants}), and each Rodada is played out whole to check that everyone in it acts exactly
 * once, highest Iniciativa first, and that the Rodada wraps right after its last Turn.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SceneTurnOrderIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0").withReplicaSet();

    @Autowired
    private SceneService sceneService;

    @Autowired
    private PlayerService playerService;

    @Autowired
    private CharacterSheetService characterSheetService;

    private String playerId;
    private String sceneId;
    /** Each sheet's rolled Iniciativa, to state the expected order without trusting the code under test. */
    private final Map<String, Integer> initiativeOf = new HashMap<>();

    @BeforeEach
    void setup() {
        playerId = playerService.create(new PlayerRequest("Turn Player", "turn-player-" + UUID.randomUUID())).id();
        sceneId = sceneService.create(new SceneCreateRequest("Turn order", "URBAN", 100, 100)).id();
        initiativeOf.clear();
    }

    private String join(int initiative) {
        String sheetId = characterSheetService.create(new CharacterSheetCreateRequest(
                new CharacterDto("Combatant " + initiative, new RaceDto("HUMAN", null, null, null, null, null),
                        Sexo.MASCULINO, null, 6, null, ActionProfile.IMPULSIVO, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null),
                playerId)).id();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(sheetId, initiative, UUID.randomUUID()));
        initiativeOf.put(sheetId, initiative);
        assertOrderInvariants();
        return sheetId;
    }

    private List<String> joinAll(int... initiatives) {
        List<String> joined = new ArrayList<>();
        for (int initiative : initiatives) {
            joined.add(join(initiative));
        }
        return joined;
    }

    private TurnAdvancedEvent advance() {
        TurnAdvancedEvent event = sceneService.advanceTurn(sceneId);
        SceneResponse scene = assertOrderInvariants();
        assertEquals(scene.currentRound(), event.currentRound());
        assertEquals(scene.currentIndex(), event.currentIndex());
        assertEquals(scene.participants().get(event.currentIndex()).characterSheetId(), event.characterSheetId());
        assertEquals(rotationOf(scene), event.rotation(), "the event carries the stored rotation, in order");
        return event;
    }

    /**
     * Plays one whole Rodada from its first Turn, through to the first Turn of the next one, and returns who acted
     * in it, in order. Expects the cursor to sit right before the Rodada's first Turn: -1, or the previous Rodada's
     * last Turn.
     */
    private List<String> playRodada() {
        TurnAdvancedEvent first = advance();
        int rodada = first.currentRound();
        assertEquals(0, first.currentIndex(), "a Rodada starts at the top of the order");
        List<String> acted = new ArrayList<>(List.of(first.characterSheetId()));
        int size = first.rotation().size();
        for (int turn = 1; turn < size; turn++) {
            TurnAdvancedEvent next = advance();
            assertEquals(rodada, next.currentRound(), "the Rodada must not wrap before everyone in it acted");
            assertEquals(turn, next.currentIndex());
            acted.add(next.characterSheetId());
        }
        return acted;
    }

    /** The checks every stored order must pass, after any change; returns the scene it checked. */
    private SceneResponse assertOrderInvariants() {
        SceneResponse scene = sceneService.get(sceneId);
        List<SceneParticipantResponse> participants = scene.participants();
        int round = scene.currentRound();
        int rotationSize = rotationOf(scene).size();
        for (int i = 0; i < participants.size(); i++) {
            boolean inRotation = participants.get(i).joinedAtRound() <= round;
            assertEquals(i < rotationSize, inRotation,
                    "everyone in the rotation stands ahead of everyone waiting: " + participants);
            if (!inRotation) {
                assertEquals(round + 1, participants.get(i).joinedAtRound(), "a waiting participant joins next Rodada");
            }
        }
        List<Integer> rotationInitiatives = participants.subList(0, rotationSize).stream()
                .map(SceneParticipantResponse::initiativeValue).toList();
        assertEquals(rotationInitiatives.stream().sorted(Comparator.reverseOrder()).toList(), rotationInitiatives,
                "the rotation is in Iniciativa order");
        assertTrue(scene.currentIndex() >= -1 && scene.currentIndex() < Math.max(rotationSize, 1),
                "the cursor stays inside the rotation: " + scene.currentIndex());
        return scene;
    }

    private static List<String> rotationOf(SceneResponse scene) {
        return scene.participants().stream()
                .filter(participant -> participant.joinedAtRound() <= scene.currentRound())
                .map(SceneParticipantResponse::characterSheetId)
                .toList();
    }

    /** ids highest Iniciativa first — the order a Rodada is expected to run in. */
    private List<String> byInitiative(List<String> ids) {
        return ids.stream().sorted(Comparator.comparingInt((String id) -> initiativeOf.get(id)).reversed()).toList();
    }

    private static List<String> concat(List<String> first, List<String> second) {
        List<String> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    @Test
    void aCrowdJoiningBeforeCombatActsInIniciativaOrderFromRodadaZero() {
        List<String> crowd = joinAll(7, 19, 3, 12, 15, 1, 22, 9, 14, 5);

        SceneResponse started = sceneService.startCombat(sceneId);

        assertEquals(0, started.currentRound());
        assertEquals(-1, started.currentIndex());
        assertEquals(byInitiative(crowd), rotationOf(started));
        assertEquals(byInitiative(crowd), playRodada());
        assertEquals(byInitiative(crowd), playRodada());
        TurnAdvancedEvent rodadaThree = advance();
        assertEquals(2, rodadaThree.currentRound());
        assertEquals(byInitiative(crowd).get(0), rodadaThree.characterSheetId());
    }

    @Test
    void joinersDuringEachRodadaWaitForTheNextOneAndNeverCutTheCurrentOneShort() {
        List<String> everyone = new ArrayList<>(joinAll(10, 20, 5, 15));
        sceneService.startCombat(sceneId);

        // Rodada 0: two join after the first Turn, one more after the third.
        TurnAdvancedEvent turn = advance();
        assertEquals(0, turn.currentRound());
        List<String> rodadaZeroJoiners = joinAll(18, 2);
        advance();
        advance();
        rodadaZeroJoiners = concat(rodadaZeroJoiners, List.of(join(11)));
        TurnAdvancedEvent last = advance();
        assertEquals(0, last.currentRound(), "three joiners did not shorten Rodada 0");
        assertEquals(3, last.currentIndex());
        assertEquals(byInitiative(everyone), last.rotation());
        everyone.addAll(rodadaZeroJoiners);

        // Rodada 1: all seven, sorted; four more join at different points of it.
        TurnAdvancedEvent rodadaOne = advance();
        assertEquals(1, rodadaOne.currentRound());
        assertEquals(byInitiative(everyone), rodadaOne.rotation());
        List<String> rodadaOneJoiners = new ArrayList<>(joinAll(25, 0));
        for (int i = 1; i < 5; i++) {
            advance();
        }
        rodadaOneJoiners.addAll(joinAll(13, 8));
        advance();
        TurnAdvancedEvent rodadaOneLast = advance();
        assertEquals(1, rodadaOneLast.currentRound());
        assertEquals(6, rodadaOneLast.currentIndex());
        everyone.addAll(rodadaOneJoiners);

        // Rodada 2 and 3: all eleven, once each, highest first.
        assertEquals(byInitiative(everyone), playRodada());
        assertEquals(byInitiative(everyone), playRodada());
        assertEquals(11, everyone.size());
    }

    @Test
    void endingCombatPutsEveryoneBackInTheOrderFromTheTop() {
        List<String> everyone = new ArrayList<>(joinAll(10, 20, 5));
        sceneService.startCombat(sceneId);
        playRodada();
        everyone.add(join(12));          // joins Rodada 1
        playRodada();
        everyone.add(join(30));          // joins Rodada 2
        playRodada();
        everyone.add(join(7));           // still waiting for Rodada 3 when combat ends
        assertEquals(2, sceneService.get(sceneId).currentRound());

        SceneResponse ended = sceneService.endCombat(sceneId);

        assertFalse(ended.combatScene());
        assertEquals(0, ended.currentRound());
        assertEquals(-1, ended.currentIndex());
        assertEquals(byInitiative(everyone), rotationOf(ended), "nobody is left waiting for a Rodada that won't come");
        assertTrue(ended.participants().stream().allMatch(participant -> participant.joinedAtRound() == 0));
        assertOrderInvariants();
    }

    @Test
    void aNewCombatAfterOneEndedRunsItsFirstRodadaWithEveryoneAndStartsAtTheTop() {
        List<String> everyone = new ArrayList<>(joinAll(10, 20, 5, 16));
        sceneService.startCombat(sceneId);
        playRodada();
        everyone.addAll(joinAll(12, 3));
        playRodada();
        playRodada();
        advance();
        advance();
        everyone.add(join(18));
        sceneService.endCombat(sceneId);

        // Out of combat, newcomers go straight in: the cursor is before the first Turn again.
        everyone.addAll(joinAll(9, 21));
        assertEquals(byInitiative(everyone), rotationOf(sceneService.get(sceneId)));

        SceneResponse started = sceneService.startCombat(sceneId);
        assertEquals(0, started.currentRound());
        assertEquals(-1, started.currentIndex());

        assertEquals(byInitiative(everyone), playRodada(), "the second combat's Rodada 0 is everyone, from the top");
        assertEquals(byInitiative(everyone), playRodada());
        assertEquals(1, sceneService.get(sceneId).currentRound());
    }

    @Test
    void severalCombatsInARowEachStartAtRodadaZeroIndexZero() {
        List<String> everyone = new ArrayList<>(joinAll(4, 14, 9));
        for (int combat = 0; combat < 3; combat++) {
            sceneService.startCombat(sceneId);
            TurnAdvancedEvent opening = advance();
            assertEquals(0, opening.currentRound(), "combat " + combat);
            assertEquals(0, opening.currentIndex(), "combat " + combat);
            assertEquals(byInitiative(everyone).get(0), opening.characterSheetId(), "combat " + combat);

            // Mid-way into the next Rodada, someone joins and the GM stops the fight.
            for (int i = 1; i < everyone.size() + 2; i++) {
                advance();
            }
            everyone.add(join(1 + combat * 10));
            sceneService.endCombat(sceneId);
            assertOrderInvariants();
        }
        sceneService.startCombat(sceneId);
        assertEquals(byInitiative(everyone), playRodada());
    }

    @Test
    void someoneWhoJoinedWhileTurnsCycledOutsideCombatActsInTheFirstRodada() {
        List<String> everyone = new ArrayList<>(joinAll(8, 15));
        advance();
        advance();
        advance();                       // cycling outside combat: Rodada stays 0
        String latecomer = join(11);
        assertEquals(1, sceneService.get(sceneId).participants().stream()
                .filter(participant -> participant.characterSheetId().equals(latecomer))
                .findFirst().orElseThrow().joinedAtRound());
        everyone.add(latecomer);

        sceneService.startCombat(sceneId);

        assertEquals(byInitiative(everyone), playRodada());
    }

    @Test
    void someoneLeavingMidRodadaNeitherSkipsNorRepeatsATurn() {
        List<String> everyone = new ArrayList<>(joinAll(30, 25, 20, 15, 10, 5));
        sceneService.startCombat(sceneId);
        advance();
        advance();                       // 25 is acting
        String waiting = join(27);

        sceneService.removeParticipant(sceneId, everyone.get(0));   // someone who already acted
        assertOrderInvariants();
        assertEquals(everyone.get(2), advance().characterSheetId());
        sceneService.removeParticipant(sceneId, everyone.get(3));   // someone yet to act
        assertOrderInvariants();
        assertEquals(everyone.get(4), advance().characterSheetId());
        assertEquals(everyone.get(5), advance().characterSheetId());

        TurnAdvancedEvent rodadaOne = advance();
        assertEquals(1, rodadaOne.currentRound());
        assertEquals(byInitiative(List.of(everyone.get(1), everyone.get(2), everyone.get(4), everyone.get(5), waiting)),
                rodadaOne.rotation());
    }
}
