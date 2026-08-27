package org.aventyrs.api.sheet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.aventyrs.core.action.ActionProfile;
import org.aventyrs.core.character.AttributeDomain;
import org.aventyrs.core.character.Character.Sexo;
import org.aventyrs.core.character.CharacterStatus;
import org.aventyrs.core.character.Deity;
import org.aventyrs.core.character.EgoDomain;
import org.aventyrs.core.character.SizeCategory;
import org.aventyrs.core.skill.SkillType;

/**
 * A CharacterSheet's embedded Character identity and build fields. {@code tendencia},
 * {@code sizeCategory}, {@code status}, {@code actionPoints}, {@code
 * temporaryActionPointsBonus}, {@code reactions}, {@code freeActions}, and {@code manaMultiplier}
 * are all nullable so a caller can omit them and get core's own defaults rather than silently
 * binding to 0/null, same reasoning as {@code CharacterSheetService#normalizeTemporaryEgoPoints}.
 * {@code attributes}/{@code egos} are nullable/partial the same way — any {@link AttributeDomain}
 * left out defaults to base 1/racialBonus 0/variable 0, and any {@link EgoDomain} left out
 * defaults to base 2/variable 0. {@code skills}/{@code egoAdvantages} only need entries for
 * Perícias actually trained / Vantagens actually chosen; a missing {@link SkillType}/{@link
 * EgoDomain} key means untrained/unchosen, so it's left as-is rather than defaulted. {@code
 * actionProfile} is required, unlike those: core's own {@code Character#actionProfile} is
 * {@code @NonNull} with no default, since it's the Perfil de Ação chosen once at character
 * creation. {@code attributeAbilities}/{@code activeAbilities}/{@code feats}/{@code equipment}
 * are nullable and carry each entry as its implementing type's {@code name()} (or, for a
 * non-enum implementer, its class's simple name); {@code egoAdvantages} does the same per
 * domain — see {@code CharacterEntry} for why these are stored as names. {@code primaryTitle}/
 * {@code secondaryTitle}/{@code tertiaryTitle} are nullable, one per Título slot.
 */
public record CharacterDto(
        @NotBlank String name,
        @NotNull @Valid RaceDto race,
        Sexo sexo,
        Deity deity,
        Integer tendencia,
        SizeCategory sizeCategory,
        @NotNull ActionProfile actionProfile,
        Map<AttributeDomain, @Valid AttributeValueDto> attributes,
        Map<EgoDomain, @Valid EgoValueDto> egos,
        Map<SkillType, @Valid CharacterSkillDto> skills,
        List<String> attributeAbilities,
        Map<EgoDomain, String> egoAdvantages,
        List<String> activeAbilities,
        Integer actionPoints,
        Integer temporaryActionPointsBonus,
        CharacterStatus status,
        Integer reactions,
        Integer freeActions,
        Integer manaMultiplier,
        Integer lifeMultiplier,
        Integer determinationMultiplier,
        Boolean centelhaSuperiorSelected,
        List<String> feats,
        List<String> equipment,
        @Valid TitleDto primaryTitle,
        @Valid TitleDto secondaryTitle,
        @Valid TitleDto tertiaryTitle
) {
}
