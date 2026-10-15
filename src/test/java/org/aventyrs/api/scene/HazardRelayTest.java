package org.aventyrs.api.scene;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.aventyrs.api.analytics.AnalyticsRecorder;
import org.aventyrs.api.monster.MonsterSheetService;
import org.aventyrs.api.scene.dto.HazardMessage;
import org.aventyrs.api.sheet.CharacterSheetService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** The GM's falls and asphyxia (core 0.1.6.14): relayed to the client that owns the target, never persisted here. */
class HazardRelayTest {

    private final SceneService sceneService = mock(SceneService.class);
    private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    private final SceneRealtimeController controller = new SceneRealtimeController(sceneService,
            mock(CharacterSheetService.class), mock(MonsterSheetService.class), template, mock(AnalyticsRecorder.class));

    @Test
    void aFallIsRelayedToTheTable() {
        HazardMessage fall = new HazardMessage("hero-1", HazardMessage.Kind.FALL, 8);

        controller.hazard("scene-1", fall);

        verify(sceneService).requireParticipant("scene-1", "hero-1");
        verify(template).convertAndSend("/topic/scenes/scene-1/hazards", fall);
    }

    @Test
    void breathingNeedsNoHeight() {
        HazardMessage submerge = new HazardMessage("hero-1", HazardMessage.Kind.STOP_BREATHING, null);

        controller.hazard("scene-1", submerge);

        verify(template).convertAndSend("/topic/scenes/scene-1/hazards", submerge);
    }

    @Test
    void aMalformedOrForeignHazardIsDropped() {
        controller.hazard("scene-1", new HazardMessage("hero-1", HazardMessage.Kind.FALL, null));
        controller.hazard("scene-1", new HazardMessage("hero-1", HazardMessage.Kind.FALL, -1));
        controller.hazard("scene-1", new HazardMessage("hero-1", null, null));
        doThrow(new RuntimeException("not a participant")).when(sceneService).requireParticipant(anyString(), eq("ghost"));
        controller.hazard("scene-1", new HazardMessage("ghost", HazardMessage.Kind.BREATHE, null));

        verify(template, never()).convertAndSend(anyString(), any(Object.class));
    }
}
