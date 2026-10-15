package org.aventyrs.api.monster.dto;

import java.util.List;

/** {@code SummonEntry} on the wire — see it. {@code null} on a foe built from a blueprint. */
public record SummonDto(String kind, int conjuradorManaGraduation, List<String> powers,
                        String casterCharacterSheetId,
                        org.aventyrs.core.magic.invocation.SummonEnhancement enhancement,
                        org.aventyrs.core.title.bruxo.Familiar familiar,
                        String bodySize, boolean butcher,
                        /** Whether it obeys nobody — Festim dos Mortos' excess, Horda Faminta (core 0.1.6.26). */
                        boolean uncontrolled) {
}
