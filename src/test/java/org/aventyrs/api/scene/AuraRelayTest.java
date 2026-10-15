package org.aventyrs.api.scene;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.aventyrs.api.analytics.AnalyticsRecorder;
import org.aventyrs.api.monster.MonsterSheetService;
import org.aventyrs.api.scene.dto.AuraMessage;
import org.aventyrs.api.sheet.CharacterSheetService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** Monster auras (core 0.1.7.4): the GM's client's grants, hits and footprints, relayed to the table, never persisted. */
class AuraRelayTest {

    private final SceneService sceneService = mock(SceneService.class);
    private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    private final SceneRealtimeController controller = new SceneRealtimeController(sceneService,
            mock(CharacterSheetService.class), mock(MonsterSheetService.class), template, mock(AnalyticsRecorder.class));

    @Test
    void aTargetsAuraStateIsRelayed() {
        AuraMessage state = new AuraMessage(AuraMessage.Kind.STATE, null, "hero-1",
                List.of(new AuraMessage.AuraGrantDto("Aura do Paradoxo", "ACTION_POINTS", -2, List.of(), 1)), null, null);

        controller.aura("scene-1", state);

        verify(sceneService).requireParticipant("scene-1", "hero-1");
        verify(template).convertAndSend("/topic/scenes/scene-1/aura", state);
    }

    @Test
    void aHitNamesItsHolderAndTarget() {
        AuraMessage hit = new AuraMessage(AuraMessage.Kind.HIT, "lich-1", "hero-1", null,
                new AuraMessage.AuraHitDto(4, "MAGICO", null, "PROFANO", 0, List.of(), 0, 0, false), null);

        controller.aura("scene-1", hit);

        verify(sceneService).requireParticipant("scene-1", "lich-1");
        verify(sceneService).requireParticipant("scene-1", "hero-1");
        verify(template).convertAndSend("/topic/scenes/scene-1/aura", hit);
    }

    @Test
    void footprintsNeedNoParticipant() {
        AuraMessage footprints = new AuraMessage(AuraMessage.Kind.FOOTPRINTS, null, null, null, null,
                List.of(new AuraMessage.AuraFootprintDto("roc-1", "Aura dos Ventos Caóticos", 4, true)));

        controller.aura("scene-1", footprints);

        verify(template).convertAndSend("/topic/scenes/scene-1/aura", footprints);
    }

    @Test
    void aMalformedOrForeignMessageIsDropped() {
        controller.aura("scene-1", new AuraMessage(null, null, "hero-1", List.of(), null, null));
        controller.aura("scene-1", new AuraMessage(AuraMessage.Kind.HIT, null, "hero-1", null, null, null));
        controller.aura("scene-1", new AuraMessage(AuraMessage.Kind.STATE, null, "hero-1", null, null, null));
        doThrow(new RuntimeException("not a participant")).when(sceneService).requireParticipant(anyString(), eq("ghost"));
        controller.aura("scene-1", new AuraMessage(AuraMessage.Kind.STATE, null, "ghost", List.of(), null, null));

        verify(template, never()).convertAndSend(anyString(), any(Object.class));
    }
}
