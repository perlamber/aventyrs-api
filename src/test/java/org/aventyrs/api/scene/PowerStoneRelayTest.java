package org.aventyrs.api.scene;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.aventyrs.api.analytics.AnalyticsRecorder;
import org.aventyrs.api.monster.MonsterSheetService;
import org.aventyrs.api.scene.dto.PowerStoneEventMessage;
import org.aventyrs.api.sheet.CharacterSheetService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** A Pedra do Poder bound or spent is relayed as sent and never persisted here (core 0.1.6.7). */
class PowerStoneRelayTest {

    private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    private final SceneRealtimeController controller = new SceneRealtimeController(mock(SceneService.class),
            mock(CharacterSheetService.class), mock(MonsterSheetService.class), template, mock(AnalyticsRecorder.class));

    @Test
    void aBindingIsRelayedUnchanged() {
        PowerStoneEventMessage message = new PowerStoneEventMessage("sheet-1", "BIND", "Hematita do Vendaval (Joia)",
                "Peitoral", 1, 2, 0, 0, 1, 3);

        controller.powerStone("scene-1", message);

        verify(template).convertAndSend("/topic/scenes/scene-1/power-stones", (Object) message);
    }

    @Test
    void aMessageWithoutAnActionIsDropped() {
        controller.powerStone("scene-1", new PowerStoneEventMessage("sheet-1", null, "x", null, 0, 0, 0, 0, 0, 0));

        verify(template, never()).convertAndSend(anyString(), any(Object.class));
    }
}
