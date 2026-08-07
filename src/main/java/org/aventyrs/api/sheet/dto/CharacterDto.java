package org.aventyrs.api.sheet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.aventyrs.core.action.ActionProfile;
import org.aventyrs.core.character.AttributeDomain;
import org.aventyrs.core.character.Character.Sexo;
import org.aventyrs.core.character.EgoDomain;
import org.aventyrs.core.character.SizeCategory;
import org.aventyrs.core.skill.SkillType;

/**
 * A CharacterSheet's embedded Character identity and build fields. {@code tendencia} and
 * {@code sizeCategory} are nullable so a caller can omit them and get core's own defaults (1,
 * {@code ZERO}) rather than silently binding to 0/null, same reasoning as
 * {@code CharacterSheetService#normalizeTemporaryEgoPoints}. {@code attributes}/{@code egos} are
 * nullable/partial the same way — any {@link AttributeDomain} left out defaults to base
 * 1/racialBonus 0/variable 0, and any {@link EgoDomain} left out defaults to base 2/variable 0.
 * {@code skills} only needs entries for Perícias actually trained; a missing {@link SkillType}
 * key means untrained, so it's left as-is rather than defaulted. {@code actionProfile} is
 * required, unlike those: core's own {@code Character#actionProfile} is {@code @NonNull} with no
 * default, since it's the Perfil de Ação chosen once at character creation.
 */
public record CharacterDto(
        @NotBlank String name,
        @NotBlank String race,
        Sexo sexo,
        Integer tendencia,
        SizeCategory sizeCategory,
        @NotNull ActionProfile actionProfile,
        Map<AttributeDomain, @Valid AttributeValueDto> attributes,
        Map<EgoDomain, @Valid EgoValueDto> egos,
        Map<SkillType, @Valid CharacterSkillDto> skills
) {
}
