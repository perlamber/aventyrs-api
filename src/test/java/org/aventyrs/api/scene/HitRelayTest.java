package org.aventyrs.api.scene;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.aventyrs.api.analytics.AnalyticsRecorder;
import org.aventyrs.api.monster.MonsterSheetService;
import org.aventyrs.api.scene.dto.AttackHitMessage;
import org.aventyrs.api.sheet.CharacterSheetService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** An unmitigated hit on someone another client owns: relayed to the table, never recorded. */
class HitRelayTest {

    private final SceneService sceneService = mock(SceneService.class);
    private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    private final AnalyticsRecorder analytics = mock(AnalyticsRecorder.class);
    private final SceneRealtimeController controller = new SceneRealtimeController(sceneService,
            mock(CharacterSheetService.class), mock(MonsterSheetService.class), template, analytics);

    private static AttackHitMessage hit(String attacker, String target, int raw) {
        return new AttackHitMessage(attacker, target, raw, false, false, "CORTANTE", null, "ATTACK", null, false);
    }

    @Test
    void aHitIsRelayedButNotRecorded() {
        AttackHitMessage message = hit("hero-1", "foe-1", 12);

        controller.hit("scene-1", message);

        verify(sceneService).requireParticipant("scene-1", "foe-1");
        verify(sceneService).requireParticipant("scene-1", "hero-1");
        verify(template).convertAndSend("/topic/scenes/scene-1/hits", message);
        verifyNoInteractions(analytics);
    }

    @Test
    void aHitOnSomeoneNotInTheSceneIsDropped() {
        doThrow(new RuntimeException("not a participant")).when(sceneService).requireParticipant(anyString(), eq("ghost"));

        controller.hit("scene-1", hit("hero-1", "ghost", 12));

        verify(template, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void aMalformedOrEmptyHitIsDropped() {
        controller.hit("scene-1", hit("hero-1", null, 12));
        controller.hit("scene-1", hit("hero-1", "foe-1", 0));

        verifyNoInteractions(template, analytics);
    }

    /** A critical that dealt no damage still carries its Efeitos Críticos — Sangramento owes 2PV on its own. */
    @Test
    void aHitCarryingOnlyEfeitosCriticosIsStillRelayed() {
        AttackHitMessage message = new AttackHitMessage("hero-1", "foe-1", 0, false, false, null, null, "ATTACK",
                null, true, "ACERTO_CRITICO_MENOR", java.util.List.of("SANGRAMENTO"));

        controller.hit("scene-1", message);

        verify(template).convertAndSend("/topic/scenes/scene-1/hits", message);
    }
}
