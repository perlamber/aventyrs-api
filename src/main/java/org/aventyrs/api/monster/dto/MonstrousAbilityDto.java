package org.aventyrs.api.monster.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.aventyrs.core.monster.model.MonsterModel;

/**
 * One Habilidade Monstruosa a monster holds. {@code ability} is the core constant's {@code name()}
 * within {@code model} — names are unique per Modelo, not across them, so the Modelo is part of
 * the key. {@code choice} is the pick for a Habilidade that asks for one ({@code
 * MonstrousAbility#getChoiceOptions()}), else {@code null}.
 */
public record MonstrousAbilityDto(
        @NotNull MonsterModel model,
        @NotBlank String ability,
        String choice
) {
}
