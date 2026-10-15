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
import org.aventyrs.api.scene.dto.CombatantActionMessage;
import org.aventyrs.api.sheet.CharacterSheetService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** A combatant's declared action (core 0.1.6.21): relayed to every table for its history, never persisted. */
class CombatantActionRelayTest {

    private final SceneService sceneService = mock(SceneService.class);
    private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    private final SceneRealtimeController controller = new SceneRealtimeController(sceneService,
            mock(CharacterSheetService.class), mock(MonsterSheetService.class), template, mock(AnalyticsRecorder.class));

    @Test
    void aDeclaredActionIsRelayedToTheTable() {
        CombatantActionMessage cast = new CombatantActionMessage("hero-1", CombatantActionMessage.Kind.SPELL,
                "Bola de Fogo");

        controller.combatantAction("scene-1", cast);

        verify(sceneService).requireParticipant("scene-1", "hero-1");
        verify(template).convertAndSend("/topic/scenes/scene-1/combatant-actions", cast);
    }

    @Test
    void aMalformedOrForeignActionIsDropped() {
        controller.combatantAction("scene-1", new CombatantActionMessage(null, CombatantActionMessage.Kind.MOVE, "x"));
        controller.combatantAction("scene-1", new CombatantActionMessage("hero-1", null, "x"));
        doThrow(new RuntimeException("not a participant")).when(sceneService).requireParticipant(anyString(), eq("ghost"));
        controller.combatantAction("scene-1", new CombatantActionMessage("ghost", CombatantActionMessage.Kind.MOVE, "x"));

        verify(template, never()).convertAndSend(anyString(), any(Object.class));
    }
}
