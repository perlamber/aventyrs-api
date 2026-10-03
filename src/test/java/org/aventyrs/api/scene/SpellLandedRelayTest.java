package org.aventyrs.api.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    /** Ogrificar's "Força ou Destreza" pick survives the JSON the server reads the message from (client 0.0.99). */
    @Test
    void theAtributoPickSurvivesTheWire() {
        tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
        SpellLandedMessage sent = new SpellLandedMessage("caster", "POLIMORFISMO:OGRIFICAR", false, "ally", false, 0,
                null, List.of(2, 3, 4), List.of(1, 1), null, "DEXTERITY");

        SpellLandedMessage read = mapper.readValue(mapper.writeValueAsString(sent), SpellLandedMessage.class);

        assertEquals("DEXTERITY", read.chosenAttribute());
    }

    /** Serra-Pernas's resolved Duração and its aimed-for Corrente survive the JSON too (client 0.1.0). */
    @Test
    void theDuracaoAndTheCorrenteChoiceSurviveTheWire() {
        tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
        SpellLandedMessage sent = new SpellLandedMessage("caster", "POLIMORFISMO:SERRA_PERNAS", false, "foe", true, 0,
                4, List.of(2, 3, 4), List.of(1, 1), null, null, 7, true);

        SpellLandedMessage read = mapper.readValue(mapper.writeValueAsString(sent), SpellLandedMessage.class);

        assertEquals(7, read.durationRounds());
        assertEquals(true, read.alternateChain());
    }
}
