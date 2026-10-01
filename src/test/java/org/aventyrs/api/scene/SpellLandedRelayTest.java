package org.aventyrs.api.scene;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.aventyrs.api.monster.MonsterSheetService;
import org.aventyrs.api.scene.dto.SpellLandedMessage;
import org.aventyrs.api.sheet.CharacterSheetService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** A Magia landing on a sheet another client owns is relayed as sent, and never persisted (client 0.0.94). */
class SpellLandedRelayTest {

    private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    private final SceneRealtimeController controller = new SceneRealtimeController(mock(SceneService.class),
            mock(CharacterSheetService.class), mock(MonsterSheetService.class), template);

    @Test
    void aLandedSpellIsRelayedUnchanged() {
        SpellLandedMessage message = new SpellLandedMessage("caster", "VIDA:REVIGORAR", false, "ally", false, 0, 4,
                List.of(6, 5, 5), List.of());

        controller.spellLanded("scene-1", message);

        verify(template).convertAndSend("/topic/scenes/scene-1/spells", (Object) message);
    }

    @Test
    void aMessageWithoutATargetIsDropped() {
        controller.spellLanded("scene-1", new SpellLandedMessage("caster", "VIDA:REVIGORAR", false, null, false, 0,
                null, List.of(), List.of()));

        verify(template, never()).convertAndSend(anyString(), any(Object.class));
    }
}
