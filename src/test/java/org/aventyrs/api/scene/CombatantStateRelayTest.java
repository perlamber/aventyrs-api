package org.aventyrs.api.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.aventyrs.api.monster.MonsterSheetService;
import org.aventyrs.api.scene.dto.CombatantStateChangedEvent;
import org.aventyrs.api.scene.dto.CombatantStateMessage;
import org.aventyrs.api.sheet.CharacterSheetService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** The live-state relay carries what core 0.0.67–0.0.69 added: riding, and the Ferocidade de Lacerto. */
class CombatantStateRelayTest {

    @Test
    void ridingAndFerocityAreRelayedUnchanged() {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        SceneRealtimeController controller = new SceneRealtimeController(mock(SceneService.class),
                mock(CharacterSheetService.class), mock(MonsterSheetService.class), template);

        controller.combatantState("scene-1",
                new CombatantStateMessage("sheet-1", "ZERO", null, List.of(), true, "MONTARIA", true));

        ArgumentCaptor<Object> relayed = ArgumentCaptor.forClass(Object.class);
        verify(template).convertAndSend(eq("/topic/scenes/scene-1/state"), relayed.capture());
        CombatantStateChangedEvent event = (CombatantStateChangedEvent) relayed.getValue();
        assertEquals("MONTARIA", event.riding());
        assertTrue(event.ferocious());
        assertTrue(event.compelled());
    }

    /** Core 0.1.1: whether the participant holds a Concentração — its turning false tells the table it was lost. */
    @Test
    void concentrationIsRelayedUnchanged() {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        SceneRealtimeController controller = new SceneRealtimeController(mock(SceneService.class),
                mock(CharacterSheetService.class), mock(MonsterSheetService.class), template);

        controller.combatantState("scene-1",
                new CombatantStateMessage("sheet-1", "ZERO", null, List.of(), false, null, false, true));

        ArgumentCaptor<Object> relayed = ArgumentCaptor.forClass(Object.class);
        verify(template).convertAndSend(eq("/topic/scenes/scene-1/state"), relayed.capture());
        assertTrue(((CombatantStateChangedEvent) relayed.getValue()).concentrating());
    }
}
