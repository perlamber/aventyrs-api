package org.aventyrs.api.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.aventyrs.api.monster.MonsterSheetService;
import org.aventyrs.api.scene.dto.ConditionChangeMessage;
import org.aventyrs.api.scene.dto.ConditionChangedEvent;
import org.aventyrs.api.scene.dto.ConditionEffectDto;
import org.aventyrs.api.sheet.CharacterSheetService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** The Condição relay (core 0.1.5): a change on someone another client owns, passed through unchanged. */
class ConditionRelayTest {

    private final SceneService sceneService = mock(SceneService.class);
    private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    private final SceneRealtimeController controller = new SceneRealtimeController(sceneService,
            mock(CharacterSheetService.class), mock(MonsterSheetService.class), template);

    @Test
    void aGrabIsRelayedWithItsCaptorAndMagnitudes() {
        ConditionChangeMessage grab = new ConditionChangeMessage("foe-1", "AGARRADO", ConditionChangeMessage.Op.APPLY,
                null, "hero-1", List.of(new ConditionEffectDto("LIFE_MULTIPLIER", -1, null)), null, null, null, false);

        controller.conditionChanged("scene-1", grab);

        ArgumentCaptor<Object> relayed = ArgumentCaptor.forClass(Object.class);
        verify(template).convertAndSend(eq("/topic/scenes/scene-1/conditions"), relayed.capture());
        assertEquals(ConditionChangedEvent.of(grab), relayed.getValue());
        verify(sceneService).requireParticipant("scene-1", "foe-1");
        verify(sceneService).requireParticipant("scene-1", "hero-1");
    }

    @Test
    void aChangeOnSomeoneNotInTheSceneIsDropped() {
        doThrow(new RuntimeException("not a participant")).when(sceneService).requireParticipant(anyString(), eq("ghost"));

        controller.conditionChanged("scene-1", new ConditionChangeMessage("ghost", "CAIDO",
                ConditionChangeMessage.Op.REMOVE, null, null, null, null, null, null, false));

        verify(template, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void aMalformedChangeIsDropped() {
        controller.conditionChanged("scene-1", new ConditionChangeMessage("foe-1", null,
                ConditionChangeMessage.Op.APPLY, null, null, null, null, null, null, false));

        verify(template, never()).convertAndSend(anyString(), any(Object.class));
    }
}
