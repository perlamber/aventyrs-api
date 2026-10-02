package org.aventyrs.api.monster.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.aventyrs.core.monster.model.MonsterModel;

/**
 * One Habilidade Monstruosa a monster holds. {@code ability} is the core constant's {@code name()}
 * within {@code model} — names are unique per Modelo, not across them, so the Modelo is part of
 * the key.
 *
 * <p>{@code choices} holds its picks per {@code ChoiceSpec} id ("element" → ["FOGO"], "trees" →
 * ["VOO", "IRA_DE_VULCANO"]). {@code choice} is the pre-0.0.60 single pick, still read — as the
 * Habilidade's one spec — and never written: a response always carries {@code choices}.
 */
public record MonstrousAbilityDto(
        @NotNull MonsterModel model,
        @NotBlank String ability,
        String choice,
        Map<String, List<String>> choices
) {
}
