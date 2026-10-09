package org.aventyrs.api.scene;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import org.aventyrs.api.analytics.AnalyticsEventType;
import org.aventyrs.api.analytics.AnalyticsRecorder;
import org.aventyrs.api.monster.MonsterSheetService;
import org.aventyrs.api.scene.dto.DamageDealtMessage;
import org.aventyrs.api.sheet.CharacterSheetService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** A hit reported by the target's client: relayed to the table and recorded for the analytics warehouse. */
class DamageRelayTest {

    private final SceneService sceneService = mock(SceneService.class);
    private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    private final AnalyticsRecorder analytics = mock(AnalyticsRecorder.class);
    private final SceneRealtimeController controller = new SceneRealtimeController(sceneService,
            mock(CharacterSheetService.class), mock(MonsterSheetService.class), template, analytics);

    private static DamageDealtMessage hit(String attacker, String target) {
        return new DamageDealtMessage(attacker, target, 12, 9, false, "CORTANTE", null, "ATTACK", null, true, 2, 9);
    }

    @Test
    void aHitIsRelayedAndRecordedAgainstItsAttackerAndTarget() {
        DamageDealtMessage message = hit("hero-1", "foe-1");

        controller.damage("scene-1", message);

        verify(sceneService).requireParticipant("scene-1", "foe-1");
        verify(sceneService).requireParticipant("scene-1", "hero-1");
        verify(template).convertAndSend("/topic/scenes/scene-1/damage", message);
        verify(analytics).record(AnalyticsEventType.DAMAGE, "scene-1", "hero-1", List.of("foe-1"), message);
    }

    @Test
    void damageWithNoAttackerIsStillRecorded() {
        DamageDealtMessage bleed = hit(null, "foe-1");

        controller.damage("scene-1", bleed);

        verify(analytics).record(AnalyticsEventType.DAMAGE, "scene-1", null, List.of("foe-1"), bleed);
    }

    @Test
    void aHitOnSomeoneNotInTheSceneIsDroppedAndNotRecorded() {
        doThrow(new RuntimeException("not a participant")).when(sceneService).requireParticipant(anyString(), eq("ghost"));

        controller.damage("scene-1", hit("hero-1", "ghost"));

        verify(template, never()).convertAndSend(anyString(), any(Object.class));
        verifyNoInteractions(analytics);
    }

    @Test
    void aMalformedHitIsDropped() {
        controller.damage("scene-1", hit("hero-1", null));

        verifyNoInteractions(template, analytics);
    }
}
