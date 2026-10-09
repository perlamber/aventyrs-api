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
import org.aventyrs.api.scene.dto.SubordinateChangeMessage;
import org.aventyrs.api.sheet.CharacterSheetService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** The GM's Subordinado grants (core 0.1.5.6): relayed to the client that owns the target, never persisted here. */
class SubordinateRelayTest {

    private final SceneService sceneService = mock(SceneService.class);
    private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    private final SceneRealtimeController controller = new SceneRealtimeController(sceneService,
            mock(CharacterSheetService.class), mock(MonsterSheetService.class), template, mock(AnalyticsRecorder.class));

    @Test
    void aGrantIsRelayedToTheTable() {
        SubordinateChangeMessage grant = new SubordinateChangeMessage("hero-1", SubordinateChangeMessage.Op.GRANT,
                "TORRE_DEFESAS", true, null);

        controller.subordinateChanged("scene-1", grant);

        verify(sceneService).requireParticipant("scene-1", "hero-1");
        verify(template).convertAndSend("/topic/scenes/scene-1/subordinates", grant);
    }

    @Test
    void aDismissalIsRelayed() {
        SubordinateChangeMessage dismissal = new SubordinateChangeMessage("foe-1", SubordinateChangeMessage.Op.DISMISS,
                null, false, "6b0b1d0e-0000-0000-0000-000000000001");

        controller.subordinateChanged("scene-1", dismissal);

        verify(template).convertAndSend("/topic/scenes/scene-1/subordinates", dismissal);
    }

    @Test
    void aMalformedOrForeignChangeIsDropped() {
        controller.subordinateChanged("scene-1",
                new SubordinateChangeMessage("hero-1", SubordinateChangeMessage.Op.GRANT, null, false, null));
        controller.subordinateChanged("scene-1",
                new SubordinateChangeMessage("hero-1", SubordinateChangeMessage.Op.DISMISS, null, false, null));
        doThrow(new RuntimeException("not a participant")).when(sceneService).requireParticipant(anyString(), eq("ghost"));
        controller.subordinateChanged("scene-1",
                new SubordinateChangeMessage("ghost", SubordinateChangeMessage.Op.GRANT, "PEAO_SKILL", false, null));

        verify(template, never()).convertAndSend(anyString(), any(Object.class));
    }
}
