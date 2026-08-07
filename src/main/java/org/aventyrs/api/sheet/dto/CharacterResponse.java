package org.aventyrs.api.sheet.dto;

import java.util.Map;
import org.aventyrs.core.action.ActionProfile;
import org.aventyrs.core.character.AttributeDomain;
import org.aventyrs.core.character.Character.Sexo;
import org.aventyrs.core.character.EgoDomain;
import org.aventyrs.core.character.SizeCategory;
import org.aventyrs.core.skill.SkillType;

public record CharacterResponse(
        String id,
        String name,
        String race,
        Sexo sexo,
        int tendencia,
        SizeCategory sizeCategory,
        ActionProfile actionProfile,
        Map<AttributeDomain, AttributeValueResponse> attributes,
        Map<EgoDomain, EgoValueResponse> egos,
        Map<SkillType, CharacterSkillResponse> skills
) {
}
