package org.aventyrs.api.sheet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aventyrs.api.item.dto.InventoryItemDto;
import org.aventyrs.core.action.ActionProfile;
import org.aventyrs.core.character.AttributeDomain;
import org.aventyrs.core.character.Character.Sexo;
import org.aventyrs.core.character.CharacterStatus;
import org.aventyrs.core.character.Deity;
import org.aventyrs.core.character.EgoDomain;
import org.aventyrs.core.character.SizeCategory;
import org.aventyrs.core.skill.SkillType;

/**
 * A CharacterSheet's embedded Character identity and build fields. {@code alignment},
 * {@code sizeCategory}, {@code status}, {@code actionPoints}, {@code
 * temporaryActionPointsBonus}, {@code reactions}, {@code freeActions}, and {@code manaMultiplier}
 * are all nullable so a caller can omit them and get core's own defaults rather than silently
 * binding to 0/null, same reasoning as {@code CharacterSheetService#normalizeTemporaryEgoPoints}.
 * {@code attributes}/{@code egos} are nullable/partial the same way — any {@link AttributeDomain}
 * left out defaults to base 1/racialBonus 0/variable 0, and any {@link EgoDomain} left out
 * defaults to base 2/variable 0. {@code alignment} is the 1–10 tendência and defaults to
 * {@code Alignment.DEFAULT} (6, Neutro) when omitted, matching core's own {@code Character#alignment}
 * default. {@code skills}/{@code egoAdvantages} only need entries for
 * Perícias actually trained / Vantagens actually chosen; a missing {@link SkillType}/{@link
 * EgoDomain} key means untrained/unchosen, so it's left as-is rather than defaulted. {@code
 * actionProfile} is required, unlike those: core's own {@code Character#actionProfile} is
 * {@code @NonNull} with no default, since it's the Perfil de Ação chosen once at character
 * creation. {@code attributeAbilities}/{@code activeAbilities} are nullable and carry each entry
 * as its implementing type's {@code name()} (or, for a non-enum implementer, its class's simple
 * name); {@code egoAdvantages} does the same per domain — see {@code CharacterEntry} for why these
 * are stored as names. {@code feats} is nullable the same way but carries full {@link FeatDto}
 * entries, since a Talento can record an acquisition-time choice that a bare name would lose — see
 * {@code FeatEntry}. {@code equipment} is nullable too, but
 * carries full {@link InventoryItemDto} entries instead — see {@code CharacterEntry}'s own
 * javadoc for why a worn item is embedded rather than stored as a name or foreign id. {@code
 * primaryTitle}/{@code
 * secondaryTitle}/{@code tertiaryTitle} are nullable, one per Título slot.
 */
public record CharacterDto(
        @NotBlank String name,
        @NotNull @Valid RaceDto race,
        Sexo sexo,
        Deity deity,
        @Min(1) @Max(10) Integer alignment,
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
        List<@Valid FeatDto> feats,
        List<@Valid InventoryItemDto> equipment,
        @Valid TitleDto primaryTitle,
        @Valid TitleDto secondaryTitle,
        @Valid TitleDto tertiaryTitle,
        // Magias learned/mimetized, nullable like attributeAbilities and stored the same
        // "constant's own name()" way — see CharacterEntry. No screen grants a Magia yet, so these exist
        // purely so a PUT round-trips what CharacterSheetService last returned.
        List<String> spells,
        List<@Valid MimetizedSpellDto> mimetizedSpells,
        // Aprendizado Rápido's two Perícias (core Character#getQuickLearningSkills) and the Centelhas
        // the character still has (Character#getCentelhas) — null reads as "none recorded" / 3.
        Set<SkillType> quickLearningSkills,
        Integer centelhas,
        // The two Antecedentes (core Character#getBackgrounds) — null reads as none chosen.
        List<@Valid BackgroundDto> backgrounds,
        // Defeitos (in force and overcome) and Qualidades — null reads as none. See DefectEntry/QualityEntry.
        List<@Valid DefectDto> defects,
        List<@Valid QualityDto> qualities,
        org.aventyrs.core.character.DevotionTier devotionTier,
        List<org.aventyrs.api.sheet.dto.DevotionPickDto> devotionPicks,
        // Aliado da Natureza's trained creatures (core Character#getTrainedCompanions) — null reads as none.
        List<org.aventyrs.api.sheet.dto.TrainedCompanionDto> trainedCompanions
) {
    /** The shape before core 0.0.103 added Aliado da Natureza's trained creatures — none. */
    public CharacterDto(@NotBlank String name, @NotNull RaceDto race, Sexo sexo, Deity deity, Integer alignment, SizeCategory sizeCategory, @NotNull ActionProfile actionProfile, Map<AttributeDomain, AttributeValueDto> attributes, Map<EgoDomain, EgoValueDto> egos, Map<SkillType, CharacterSkillDto> skills, List<String> attributeAbilities, Map<EgoDomain, String> egoAdvantages, List<String> activeAbilities, Integer actionPoints, Integer temporaryActionPointsBonus, CharacterStatus status, Integer reactions, Integer freeActions, Integer manaMultiplier, Integer lifeMultiplier, Integer determinationMultiplier, Boolean centelhaSuperiorSelected, List<FeatDto> feats, List<InventoryItemDto> equipment, TitleDto primaryTitle, TitleDto secondaryTitle, TitleDto tertiaryTitle, List<String> spells, List<MimetizedSpellDto> mimetizedSpells, Set<SkillType> quickLearningSkills, Integer centelhas, List<BackgroundDto> backgrounds, List<DefectDto> defects, List<QualityDto> qualities, org.aventyrs.core.character.DevotionTier devotionTier, List<org.aventyrs.api.sheet.dto.DevotionPickDto> devotionPicks) {
        this(name, race, sexo, deity, alignment, sizeCategory, actionProfile, attributes, egos, skills, attributeAbilities, egoAdvantages, activeAbilities, actionPoints, temporaryActionPointsBonus, status, reactions, freeActions, manaMultiplier, lifeMultiplier, determinationMultiplier, centelhaSuperiorSelected, feats, equipment, primaryTitle, secondaryTitle, tertiaryTitle, spells, mimetizedSpells, quickLearningSkills, centelhas, backgrounds, defects, qualities, devotionTier, devotionPicks, null);
    }

    /** The shape before core 0.0.86 added the devotion tier and its rung picks — none set. */
    public CharacterDto(String name, RaceDto race, Sexo sexo, Deity deity, Integer alignment, SizeCategory sizeCategory, ActionProfile actionProfile, Map<AttributeDomain, AttributeValueDto> attributes, Map<EgoDomain, EgoValueDto> egos, Map<SkillType, CharacterSkillDto> skills, List<String> attributeAbilities, Map<EgoDomain, String> egoAdvantages, List<String> activeAbilities, Integer actionPoints, Integer temporaryActionPointsBonus, CharacterStatus status, Integer reactions, Integer freeActions, Integer manaMultiplier, Integer lifeMultiplier, Integer determinationMultiplier, Boolean centelhaSuperiorSelected, List<FeatDto> feats, List<InventoryItemDto> equipment, TitleDto primaryTitle, TitleDto secondaryTitle, TitleDto tertiaryTitle, List<String> spells, List<MimetizedSpellDto> mimetizedSpells, Set<SkillType> quickLearningSkills, Integer centelhas, List<BackgroundDto> backgrounds, List<DefectDto> defects, List<QualityDto> qualities) {
        this(name, race, sexo, deity, alignment, sizeCategory, actionProfile, attributes, egos, skills, attributeAbilities, egoAdvantages, activeAbilities, actionPoints, temporaryActionPointsBonus, status, reactions, freeActions, manaMultiplier, lifeMultiplier, determinationMultiplier, centelhaSuperiorSelected, feats, equipment, primaryTitle, secondaryTitle, tertiaryTitle, spells, mimetizedSpells, quickLearningSkills, centelhas, backgrounds, defects, qualities, null, null);
    }

    /** The shape before core 0.0.72 added the Defeitos e Qualidades. */
    public CharacterDto(String name,
            RaceDto race,
            Sexo sexo,
            Deity deity,
            Integer alignment,
            SizeCategory sizeCategory,
            ActionProfile actionProfile,
            Map<AttributeDomain, AttributeValueDto> attributes,
            Map<EgoDomain, EgoValueDto> egos,
            Map<SkillType, CharacterSkillDto> skills,
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
            List<FeatDto> feats,
            List<InventoryItemDto> equipment,
            TitleDto primaryTitle,
            TitleDto secondaryTitle,
            TitleDto tertiaryTitle,
            List<String> spells,
            List<MimetizedSpellDto> mimetizedSpells,
            Set<SkillType> quickLearningSkills,
            Integer centelhas,
            List<BackgroundDto> backgrounds) {
        this(name, race, sexo, deity, alignment, sizeCategory, actionProfile, attributes, egos, skills, attributeAbilities, egoAdvantages, activeAbilities, actionPoints, temporaryActionPointsBonus, status, reactions, freeActions, manaMultiplier, lifeMultiplier, determinationMultiplier, centelhaSuperiorSelected, feats, equipment, primaryTitle, secondaryTitle, tertiaryTitle, spells, mimetizedSpells, quickLearningSkills, centelhas, backgrounds, null, null);
    }

    /** The shape before core 0.0.71 added the Antecedentes. */
    public CharacterDto(String name,
            RaceDto race,
            Sexo sexo,
            Deity deity,
            Integer alignment,
            SizeCategory sizeCategory,
            ActionProfile actionProfile,
            Map<AttributeDomain, AttributeValueDto> attributes,
            Map<EgoDomain, EgoValueDto> egos,
            Map<SkillType, CharacterSkillDto> skills,
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
            List<FeatDto> feats,
            List<InventoryItemDto> equipment,
            TitleDto primaryTitle,
            TitleDto secondaryTitle,
            TitleDto tertiaryTitle,
            List<String> spells,
            List<MimetizedSpellDto> mimetizedSpells,
            Set<SkillType> quickLearningSkills,
            Integer centelhas) {
        this(name, race, sexo, deity, alignment, sizeCategory, actionProfile, attributes, egos, skills, attributeAbilities, egoAdvantages, activeAbilities, actionPoints, temporaryActionPointsBonus, status, reactions, freeActions, manaMultiplier, lifeMultiplier, determinationMultiplier, centelhaSuperiorSelected, feats, equipment, primaryTitle, secondaryTitle, tertiaryTitle, spells, mimetizedSpells, quickLearningSkills, centelhas, null);
    }

    /** The shape before core 0.0.67/0.0.68 added Aprendizado Rápido and Centelhas. */
    public CharacterDto(String name,
                        RaceDto race,
                        Sexo sexo,
                        Deity deity,
                        Integer alignment,
                        SizeCategory sizeCategory,
                        ActionProfile actionProfile,
                        Map<AttributeDomain, AttributeValueDto> attributes,
                        Map<EgoDomain, EgoValueDto> egos,
                        Map<SkillType, CharacterSkillDto> skills,
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
                        List<FeatDto> feats,
                        List<InventoryItemDto> equipment,
                        TitleDto primaryTitle,
                        TitleDto secondaryTitle,
                        TitleDto tertiaryTitle,
                        List<String> spells,
                        List<MimetizedSpellDto> mimetizedSpells) {
        this(name, race, sexo, deity, alignment, sizeCategory, actionProfile, attributes, egos, skills,
                attributeAbilities, egoAdvantages, activeAbilities, actionPoints, temporaryActionPointsBonus,
                status, reactions, freeActions, manaMultiplier, lifeMultiplier, determinationMultiplier,
                centelhaSuperiorSelected, feats, equipment, primaryTitle, secondaryTitle, tertiaryTitle,
                spells, mimetizedSpells, null, null);
    }
}
