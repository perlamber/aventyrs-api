package org.aventyrs.api.analytics;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.aventyrs.api.player.PlayerService;
import org.aventyrs.api.player.dto.PlayerRequest;
import org.aventyrs.api.scene.AttackSourceKind;
import org.aventyrs.api.scene.SceneService;
import org.aventyrs.api.scene.dto.AddParticipantRequest;
import org.aventyrs.api.scene.dto.RecordActionMessage;
import org.aventyrs.api.scene.dto.SceneActionEvent;
import org.aventyrs.api.scene.dto.SceneCreateRequest;
import org.aventyrs.api.sheet.CharacterSheetService;
import org.aventyrs.api.sheet.dto.CharacterDto;
import org.aventyrs.api.sheet.dto.CharacterSheetCreateRequest;
import org.aventyrs.api.sheet.dto.RaceDto;
import org.aventyrs.core.action.ActionProfile;
import org.aventyrs.core.character.Alignment;
import org.aventyrs.core.character.Character.Sexo;
import org.aventyrs.core.character.CharacterStatus;
import org.aventyrs.core.sheet.ActionCost;
import org.aventyrs.core.skill.SkillType;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

/** The capture half of the analytics warehouse: what an accepted frame leaves behind in {@code analytics_events}. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AnalyticsRecorderIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0").withReplicaSet();

    @Autowired
    private AnalyticsRecorder recorder;

    @Autowired
    private SheetSnapshotService snapshots;

    @Autowired
    private SceneService sceneService;

    @Autowired
    private CharacterSheetService characterSheetService;

    @Autowired
    private PlayerService playerService;

    @Autowired
    private MongoTemplate mongoTemplate;

    private String attackerId;
    private String targetId;
    private String sceneId;

    @BeforeEach
    void createSceneWithTwoCombatants() {
        String playerId = playerService
                .create(new PlayerRequest("Analytics Player", "analytics-player-" + UUID.randomUUID()))
                .id();
        attackerId = createCharacterSheet(playerId);
        targetId = createCharacterSheet(playerId);
        sceneId = sceneService.create(new SceneCreateRequest("Arena", "URBAN", 20, 20)).id();
        sceneService.addParticipant(sceneId, new AddParticipantRequest(attackerId, 15, UUID.randomUUID()));
        sceneService.addParticipant(sceneId, new AddParticipantRequest(targetId, 10, UUID.randomUUID()));
    }

    private String createCharacterSheet(String playerId) {
        return characterSheetService.create(new CharacterSheetCreateRequest(
                new CharacterDto("Analytics Character", new RaceDto("HUMAN", null, null, null, null, null),
                        Sexo.MASCULINO, null, 6, null, ActionProfile.IMPULSIVO, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null),
                playerId)).id();
    }

    private List<AnalyticsEventDocument> eventsOf(AnalyticsEventType type) {
        return mongoTemplate.find(Query.query(Criteria.where("sceneId").is(sceneId).and("type").is(type)),
                AnalyticsEventDocument.class);
    }

    @Test
    void anActionIsStampedWithTheScenesRoundItsHistoryIndexAndBothSheets() {
        sceneService.startCombat(sceneId);
        sceneService.advanceTurn(sceneId);
        int round = sceneService.get(sceneId).currentRound();
        SceneActionEvent action = sceneService.recordAction(sceneId, new RecordActionMessage(
                attackerId, SkillType.ATAQUE_CORPO_A_CORPO, null, AttackSourceKind.WEAPON, ActionCost.Kind.FIXED, 3,
                1, true, 2, null, null, List.of(3, 4, 5), 12, targetId, List.of("LUTADOR_NATO")));

        recorder.record(AnalyticsEventType.ACTION, sceneId, attackerId, List.of(targetId), action);

        AnalyticsEventDocument event = eventsOf(AnalyticsEventType.ACTION).get(0);
        assertNotNull(event.occurredAt());
        assertEquals("Arena", event.sceneName());
        assertEquals(round, event.sceneRound());
        assertEquals(Boolean.TRUE, event.combatScene());
        assertEquals(0, event.historyIndex());
        assertEquals(List.of(targetId), event.targetCharacterSheetIds());
        assertNotNull(event.actorSheetSnapshotId());
        assertNotNull(event.targetSheetSnapshotIds().get(0));
        Document snapshot = mongoTemplate.findById(event.actorSheetSnapshotId(), Document.class,
                SheetSnapshotService.COLLECTION);
        assertEquals(attackerId, snapshot.getString("characterSheetId"));
        assertEquals("CHARACTER", snapshot.getString("kind"));
        assertNotNull(snapshot.get("sheet", Document.class).get("character"));
    }

    @Test
    void combatStartSnapshotsEveryParticipant() {
        sceneService.startCombat(sceneId);

        recorder.record(AnalyticsEventType.COMBAT_STARTED, sceneId, null, null, null);

        AnalyticsEventDocument event = eventsOf(AnalyticsEventType.COMBAT_STARTED).get(0);
        assertEquals(List.of(attackerId, targetId), event.targetCharacterSheetIds());
        assertTrue(event.targetSheetSnapshotIds().stream().allMatch(id -> id != null));
    }

    /** Pools churn on every hit; only a change to the build itself is a new snapshot. */
    @Test
    void aSnapshotIsReusedUntilTheBuildChanges() {
        String before = snapshots.snapshot(attackerId);

        characterSheetService.updateCombatStatus(attackerId, 7, 2, 1, CharacterStatus.HIGH_LIFE);
        assertEquals(before, snapshots.snapshot(attackerId), "spent PV are not a new build");

        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(attackerId)),
                new Update().set("totalExperience", 1200), SheetSnapshotService.CHARACTER_SHEETS);
        assertNotEquals(before, snapshots.snapshot(attackerId));
    }

    @Test
    void anUnknownSheetHasNoSnapshot() {
        assertNull(snapshots.snapshot("no-such-sheet"));
    }

    @Test
    void anUnknownSceneIsStillRecordedAndNeverThrows() {
        assertDoesNotThrow(() -> recorder.record(AnalyticsEventType.MOVE, "no-such-scene", attackerId, null, null));

        List<AnalyticsEventDocument> recorded = mongoTemplate.find(
                Query.query(Criteria.where("sceneId").is("no-such-scene")), AnalyticsEventDocument.class);
        assertEquals(1, recorded.size());
        assertNull(recorded.get(0).sceneRound());
    }
}
